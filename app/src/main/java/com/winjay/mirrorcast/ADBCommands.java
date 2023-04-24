package com.winjay.mirrorcast;


import static android.org.apache.commons.codec.binary.Base64.encodeBase64String;

import android.content.Context;
import android.content.res.AssetManager;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbInterface;
import android.os.Environment;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Base64;

import com.winjay.adblib.AdbBase64;
import com.winjay.adblib.AdbConnection;
import com.winjay.adblib.AdbCrypto;
import com.winjay.adblib.AdbStream;
import com.winjay.adblib.Install;
import com.winjay.adblib.Push;
import com.winjay.adblib.TcpChannel;
import com.winjay.adblib.UsbChannel;
import com.winjay.mirrorcast.util.LogUtil;
import com.winjay.mirrorcast.util.NetUtil;

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
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;


public class ADBCommands {
    private static final String TAG = ADBCommands.class.getSimpleName();
    private static volatile ADBCommands instance;

    private AdbConnection adbConnection = null;
    private AdbStream adbStream = null;

    private Thread thread = null;
    private Context mContext;

    private ADBCommands(Context context) {
        mContext = context;
    }

    public static ADBCommands getInstance(Context context) {
        if (instance == null) {
            synchronized (ADBCommands.class) {
                if (instance == null) {
                    instance = new ADBCommands(context);
                }
            }
        }
        return instance;
    }

    private AdbBase64 getBase64Impl() {
        return new AdbBase64() {
            @Override
            public String encodeToString(byte[] arg0) {
                return encodeBase64String(arg0);
            }
        };
    }

    private AdbCrypto setupCrypto() {
        AdbCrypto c = null;
        try {
            c = AdbCrypto.loadAdbKeyPair(getBase64Impl(), mContext.getFileStreamPath("priv.key"), mContext.getFileStreamPath("pub.key"));
        } catch (IOException | InvalidKeySpecException | NoSuchAlgorithmException | NullPointerException e) {
            // Failed to read from file
            e.printStackTrace();
        }

        if (c == null) {
            try {
                // We couldn't load a key, so let's generate a new one
                c = AdbCrypto.generateAdbKeyPair(getBase64Impl());
                // Save it
                c.saveAdbKeyPair(mContext.getFileStreamPath("priv.key"), mContext.getFileStreamPath("pub.key"));
                //Generated new keypair
            } catch (NoSuchAlgorithmException | IOException e) {
                e.printStackTrace();
            }
        } else {
            //Loaded existing keypair
        }
        return c;
    }

    private byte[] assembleServerJar() {
        try {
            AssetManager assetManager = mContext.getAssets();
            InputStream input_Stream = assetManager.open("scrcpy-server.jar");
            byte[] buffer = new byte[input_Stream.available()];
            input_Stream.read(buffer);
            return Base64.encode(buffer, Base64.NO_WRAP);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean sendServerJar(String serverIp) {
        Socket sock;
        try {
            sock = new Socket(serverIp, 5555);
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        try {
            adbConnection = AdbConnection.create(new TcpChannel(sock), setupCrypto());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return pushFile();
    }

    public boolean sendServerJar(UsbDeviceConnection connection, UsbInterface usbInterface) {
        try {
            adbConnection = AdbConnection.create(new UsbChannel(connection, usbInterface), setupCrypto());
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
        return pushFile();
    }

    private boolean pushFile() {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        Future<Boolean> future = executor.submit(() -> pushFile(assembleServerJar()));
        boolean result = false;
        try {
            result = future.get();
        } catch (ExecutionException | InterruptedException e) {
            e.printStackTrace();
        }
        executor.shutdown();
        LogUtil.d(TAG, "result=" + result);
        return result;
    }

    private boolean pushFile(byte[] fileBase64) {
        if (adbConnection == null) {
            return false;
        }
        try {
            adbConnection.connect();
            adbStream = adbConnection.open("shell:");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        if (adbStream == null) {
            return false;
        }
        try {
            adbStream.write(" " + '\n');
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        String responses = "";
        boolean done = false;
        while (!done) {
            try {
                byte[] responseBytes = adbStream.read();
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
                return false;
            }
        }

        int len = fileBase64.length;
        byte[] filePart = new byte[4056];
        int sourceOffset = 0;
        try {
            adbStream.write(" cd /data/local/tmp " + '\n');
            while (sourceOffset < len) {
                if (len - sourceOffset >= 4056) {
                    System.arraycopy(fileBase64, sourceOffset, filePart, 0, 4056);  //Writing in 4KB pieces. 4096-40  ---> 40 Bytes for actual command text.
                    sourceOffset = sourceOffset + 4056;
                    String ServerBase64part = new String(filePart, StandardCharsets.US_ASCII);
                    adbStream.write(" echo " + ServerBase64part + " >> serverBase64" + '\n');
                    done = false;
                    while (!done) {
                        byte[] responseBytes = adbStream.read();
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
                    adbStream.write(" echo " + ServerBase64part + " >> serverBase64" + '\n');
                    done = false;
                    while (!done) {
                        byte[] responseBytes = adbStream.read();
                        String response = new String(responseBytes, StandardCharsets.US_ASCII);
                        if (response.endsWith("$ ") || response.endsWith("# ")) {
                            done = true;
                        }
                    }
                }
            }
            adbStream.write(" base64 -d < serverBase64 > scrcpy-server.jar && rm serverBase64" + '\n');
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    public boolean startMirrorCast(String serverIp, int serverPort, int bitrate, int maxSize, String displayId) {
        LogUtil.d(TAG);
        StringBuilder command = new StringBuilder();
        command.append(" CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.winjay.scrcpy.Server ");
        command.append("1.24" + " server_ip=" + serverIp + " server_port=" + serverPort + " max_size=" + maxSize + " max_fps=30" + (TextUtils.isEmpty(displayId) ? "" : " display_id=" + displayId)); // + " display_id=10" + " bit_rate=" + bitrate
        LogUtil.d(TAG, "command=" + command);

        try {
            adbStream = adbConnection.open("shell:");
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
        if (adbStream == null) {
            return false;
        }
        try {
            adbStream.write(" " + '\n');
            adbStream.write(" cd /data/local/tmp " + '\n');
            adbStream.write(command.toString() + '\n');
            return true;
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            LogUtil.e(TAG, e.getMessage());
            return false;
        }
    }

    private void initCommand() {
        // Open the shell stream of ADB
        try {
            //TODO: DO NOT DELETE IT, I CAN'T EXPLAIN WHY
            adbConnection.open("shell:exec date");

            adbStream = adbConnection.open("shell:");
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
                while (!adbStream.isClosed()) {
                    try {
                        // Print each thing we read from the shell stream
                        final String[] output = {new String(adbStream.read(), "US-ASCII")};
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
        if (adbStream == null) {
            initCommand();
        }

        if (!cmd.isEmpty()) {
            // We become the sending thread
            try {
                adbStream.write((cmd + "\n").getBytes("UTF-8"));
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

//    public boolean stopMirrorCast() {
//        StringBuilder command = new StringBuilder();
//        command.append(" CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.winjay.scrcpy.CleanUp ");
//        if (stream != null) {
//            try {
//                stream.write(command.toString() + '\n');
//                return true;
//            } catch (IOException | InterruptedException e) {
//                LogUtil.e(TAG, "stop mirror cast error:" + e.getMessage());
//                e.printStackTrace();
//                return false;
//            }
//        }
//        return false;
//    }


    /*public int SendAdbCommands(Context context, final byte[] fileBase64, final String serverIp, String serverPort, String localIp, int bitrate, int maxSize, String displayId) {
        this.mContext = context;
        status = 1;
        final StringBuilder command = new StringBuilder();
        command.append(" CLASSPATH=/data/local/tmp/scrcpy-server.jar app_process / com.winjay.scrcpy.Server ");
        command.append("1.24" + " server_port=" + serverPort + " local_ip=" + localIp + " max_size=" + maxSize + " max_fps=30" + (TextUtils.isEmpty(displayId) ? "" : " display_id=" + displayId)); // + " display_id=10" + " bit_rate=" + bitrate

        thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    adbWrite(serverIp, fileBase64, command.toString());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
        thread.start();
        int count = 0;
        while (status == 1 && count < 1000) {
            LogUtil.d(TAG, "Connecting...");
            try {
                Thread.sleep(100);
                count++;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (count == 1000) {
            status = 2;
        }
        return status;
    }


    private void adbWrite(String serverIp, byte[] fileBase64, String command) throws IOException {
        AdbConnection adb = null;
        Socket sock = null;
        AdbCrypto crypto;
        AdbStream stream = null;

        try {
            crypto = setupCrypto();
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            throw new IOException("Couldn't read/write keys");
        }

        try {
            sock = new Socket(serverIp, 5555);
            LogUtil.d(TAG, " ADB socket connection successful");
        } catch (UnknownHostException e) {
            status = 2;
            throw new UnknownHostException(serverIp + " is no valid ip address");
        } catch (ConnectException e) {
            status = 2;
            throw new ConnectException("Device at " + serverIp + ":" + 5555 + " has no adb enabled or connection is refused");
        } catch (NoRouteToHostException e) {
            status = 2;
            throw new NoRouteToHostException("Couldn't find adb device at " + serverIp + ":" + 5555);
        } catch (IOException e) {
            e.printStackTrace();
            status = 2;
        }

        if (sock != null && status == 1) {
            try {
                adb = AdbConnection.create(sock, crypto);
                adb.connect();
            } catch (IllegalStateException e) {
                e.printStackTrace();
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        if (adb != null && status == 1) {

            try {
                stream = adb.open("shell:");
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                status = 2;
                return;
            }
        }

        if (stream != null && status == 1) {
            try {
                stream.write(" " + '\n');
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                return;
            }
        }

        String responses = "";
        boolean done = false;
        while (!done && stream != null && status == 1) {
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
                status = 2;
                e.printStackTrace();
            }
        }

        if (stream != null && status == 1) {
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
                Thread.sleep(100);
                stream.write(command + '\n');
            } catch (IOException | InterruptedException e) {
                e.printStackTrace();
                status = 2;
                return;
            }
        }
        if (status == 1) ;
        status = 0;
    }*/

}
