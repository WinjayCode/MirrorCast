package com.winjay.mirrorcast.aoa;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.hardware.usb.UsbManager;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Base64;
import android.widget.Toast;

import androidx.annotation.NonNull;

import com.winjay.adblib.AdbBase64;
import com.winjay.adblib.AdbConnection;
import com.winjay.adblib.AdbCrypto;
import com.winjay.adblib.AdbStream;
import com.winjay.adblib.Install;
import com.winjay.adblib.MyAdbBase64;
import com.winjay.adblib.Push;
import com.winjay.adblib.UsbChannel;
import com.winjay.mirrorcast.util.FileUtil;
import com.winjay.mirrorcast.util.HandlerManager;
import com.winjay.mirrorcast.util.LogUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.ConnectException;
import java.net.NoRouteToHostException;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

/**
 * @author F2848777
 * @date 2023-04-21
 */
public class OTGADBCommands {
    private static final String TAG = OTGADBCommands.class.getSimpleName();
    private static volatile OTGADBCommands INSTANCE;
    private Context context;
    private UsbManager usbManager;

    private AdbCrypto adbCrypto;
    private AdbConnection adbConnection;
    private AdbStream stream;

    private OTGADBCommands(Context context) {
        this.context = context;
        usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);

        AdbBase64 base64 = new MyAdbBase64();
        try {
            adbCrypto = AdbCrypto.loadAdbKeyPair(base64, new File(context.getFilesDir(), "private_key"), new File(context.getFilesDir(), "public_key"));
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (adbCrypto == null) {
            try {
                adbCrypto = AdbCrypto.generateAdbKeyPair(base64);
                adbCrypto.saveAdbKeyPair(new File(context.getFilesDir(), "private_key"), new File(context.getFilesDir(), "public_key"));
            } catch (Exception e) {
                LogUtil.w(TAG, "fail to generate and save key-pair", e);
            }
        }
    }

    public static OTGADBCommands getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (OTGADBCommands.class) {
                if (INSTANCE == null) {
                    INSTANCE = new OTGADBCommands(context);
                }
            }
        }
        return INSTANCE;
    }

    public void asyncRefreshAdbConnection(UsbDevice device) {
        if (device != null) {
            new Thread() {
                @Override
                public void run() {
                    final UsbInterface intf = findAdbInterface(device);
                    try {
                        setAdbInterface(device, intf);
                    } catch (Exception e) {
                        LogUtil.w(TAG, "setAdbInterface(device, intf) fail", e);
                    }
                }
            }.start();
        }
    }

    // searches for an adb interface on the given USB device
    private UsbInterface findAdbInterface(UsbDevice device) {
        int count = device.getInterfaceCount();
        for (int i = 0; i < count; i++) {
            UsbInterface intf = device.getInterface(i);
            if (intf.getInterfaceClass() == 255 && intf.getInterfaceSubclass() == 66 &&
                    intf.getInterfaceProtocol() == 1) {
                return intf;
            }
        }
        return null;
    }

    // Sets the current USB device and interface
    private synchronized void setAdbInterface(UsbDevice device, UsbInterface intf) throws IOException, InterruptedException {
        if (adbConnection != null) {
            adbConnection.close();
            adbConnection = null;
        }

        if (device != null && intf != null) {
            UsbDeviceConnection connection = usbManager.openDevice(device);
            if (connection != null) {
                if (connection.claimInterface(intf, false)) {
                    adbConnection = AdbConnection.create(new UsbChannel(connection, intf), adbCrypto);
                    adbConnection.connect();
                    //TODO: DO NOT DELETE IT, I CAN'T EXPLAIN WHY
                    adbConnection.open("shell:exec date");


                    // test
//                    putCommand("input keyevent 24");

//                    FileUtil.copyAssetsFile(context, "scrcpy-server.jar", "/sdcard/");
//                    File file = new File("/sdcard/scrcpy-server.jar");
//                    File file = new File(context.getCacheDir() + "/scrcpy-server.jar");
//                    new Push(adbConnection, file, "/data/local/tmp").execute(new Handler(Looper.getMainLooper()) {
//                        @Override
//                        public void handleMessage(@NonNull Message msg) {
//                            switch (msg.what) {
//                                case com.winjay.adblib.Message.INSTALLING_PROGRESS:
//                                    LogUtil.d(TAG, "progress=" + msg.arg2);
//                                    if ((int) msg.arg2 == 100) {
//                                        Toast.makeText(context, "Push完成！", Toast.LENGTH_SHORT).show();
//                                    }
//                                    break;
//                            }
//                        }
//                    });


                    try {
                        AssetManager assetManager = context.getAssets();
                        InputStream input_Stream = assetManager.open("scrcpy-server.jar");
                        byte[] buffer = new byte[input_Stream.available()];
                        input_Stream.read(buffer);
                        byte[] fileBase64 = Base64.encode(buffer, Base64.NO_WRAP);

                        adbWrite(fileBase64);

                        HandlerManager.getInstance().postOnMainThread(new Runnable() {
                            @Override
                            public void run() {
                                Toast.makeText(context, "Push完成！", Toast.LENGTH_LONG).show();
                            }
                        });
                    } catch (IOException e) {
                        e.printStackTrace();
                    }

                } else {
                    connection.close();
                }
            }
        }
    }

    private void initCommand() {
        // Open the shell stream of ADB
        try {
            stream = adbConnection.open("shell:");
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return;
        }

        // Start the receiving thread
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (!stream.isClosed()) {
                    try {
                        // Print each thing we read from the shell stream
                        final String[] output = {new String(stream.read(), "US-ASCII")};
//                        runOnUiThread(new Runnable() {
//                            @Override
//                            public void run() {
//                                if (user == null) {
//                                    user = output[0].substring(0,output[0].lastIndexOf("/")+1);
//                                }else if (output[0].contains(user)){
//                                    System.out.println("End => "+user);
//                                }
//
//                                logs.append(output[0]);
//                            }
//                        });
                    } catch (UnsupportedEncodingException e) {
                        e.printStackTrace();
                        return;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return;
                    }
                }
            }
        }).start();
    }

    public void putCommand(String cmd) {
        if (stream == null) {
            initCommand();
        }

        if (!cmd.isEmpty()) {
            // We become the sending thread
            try {
                stream.write((cmd + "\n").getBytes("UTF-8"));
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void test(Handler handler) {
        File local = new File(Environment.getExternalStorageDirectory() + "/adm.apk");
        String remotePath = "/sdcard/" + local.getName();
        try {
            new Push(adbConnection, local, remotePath).execute(handler);
            new Install(adbConnection, remotePath, local.length() / 1024).execute(handler);
        } catch (Exception e) {
            LogUtil.w(TAG, "exception caught", e);
        }
    }



    private void adbWrite(byte[] fileBase64) {
        if (adbConnection != null) {
            try {
                stream = adbConnection.open("shell:");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        if (stream != null) {
            try {
                stream.write(" " + '\n');
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        String responses = "";
        boolean done = false;
        while (!done && stream != null) {
            try {
                byte[] responseBytes = stream.read();
                String response = new String(responseBytes, StandardCharsets.US_ASCII);
                if (response.substring(response.length() - 2).equals("$ ") ||
                        response.substring(response.length() - 2).equals("# ")) {
                    done = true;
//                    Log.e("ADB_Shell","Prompt ready");
                    responses += response;
                    break;
                } else {
                    responses += response;
                }
            } catch (InterruptedException | IOException e) {
                e.printStackTrace();
            }
        }

        if (stream != null) {
            int len = fileBase64.length;
            byte[] filePart = new byte[4056];
            int sourceOffset = 0;
            try {
                stream.write(" cd /data/local/tmp " + '\n');
                while (sourceOffset < len) {
                    if (len - sourceOffset >= 4056) {
                        System.arraycopy(fileBase64, sourceOffset, filePart, 0, 4056);  //Writing in 4KB pieces. 4096-40  ---> 40 Bytes for actual command text.
                        sourceOffset = sourceOffset + 4056;
                        String ServerBase64part = new String(filePart, StandardCharsets.US_ASCII);
                        stream.write(" echo " + ServerBase64part + " >> serverBase64" + '\n');
                        done = false;
                        while (!done) {
                            byte[] responseBytes = stream.read();
                            String response = new String(responseBytes, StandardCharsets.US_ASCII);
                            if (response.endsWith("$ ") || response.endsWith("# ")) {
                                done = true;
                            }
                        }
                    } else {
                        int rem = len - sourceOffset;
                        byte[] remPart = new byte[rem];
                        System.arraycopy(fileBase64, sourceOffset, remPart, 0, rem);
                        sourceOffset = sourceOffset + rem;
                        String ServerBase64part = new String(remPart, StandardCharsets.US_ASCII);
                        stream.write(" echo " + ServerBase64part + " >> serverBase64" + '\n');
                        done = false;
                        while (!done) {
                            byte[] responseBytes = stream.read();
                            String response = new String(responseBytes, StandardCharsets.US_ASCII);
                            if (response.endsWith("$ ") || response.endsWith("# ")) {
                                done = true;
                            }
                        }
                    }
                }
                stream.write(" base64 -d < serverBase64 > scrcpy-server.jar && rm serverBase64" + '\n');
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }
    }
}
