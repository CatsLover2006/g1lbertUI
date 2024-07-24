package ca.litten.g1lbertUI;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import oshi.*;
import oshi.hardware.CentralProcessor;
import oshi.hardware.UsbDevice;
import oshi.software.os.OperatingSystem;
import org.apache.commons.lang3.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.*;

import static java.awt.GridBagConstraints.*;
import static java.lang.System.exit;

public class Main {
    private static JFrame frame;
    private static List<UsbDevice> root;
    private static OperatingSystem os;
    private static CentralProcessor cpu;
    private static SystemInfo sys;
    private static JLabel status;
    private static JLabel substatus;
    private static boolean idle = true;
    
    private static boolean iosDeviceConnected(List<UsbDevice> devices) {
        for (UsbDevice device : devices) {
            if (device.getVendor().contains("Apple")) {
                String deviceName = device.getName();
                if (deviceName.contains("iPod") || deviceName.contains("iPhone") || deviceName.contains("iPad")) {
                    return true;
                }
            }
            if (iosDeviceConnected(device.getConnectedDevices())) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean iosDeviceConnected() {
        return iosDeviceConnected(sys.getHardware().getUsbDevices(true));
    }
    
    public static void main(String[] args) {
        sys = new SystemInfo();
        os = sys.getOperatingSystem();
        cpu = sys.getHardware().getProcessor();
        if (SystemUtils.IS_OS_MAC_OSX) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "g1lbertUI");
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
            FlatMacDarkLaf.setup();
        } else {
            FlatDarculaLaf.setup();
        }
        if (!(SystemUtils.IS_OS_MAC_OSX || SystemUtils.IS_OS_LINUX)) {
            frame = new JFrame("OS Error");
            frame.add(new JLabel("Your OS is not supported.\nTry booting a Linux live USB."));
            frame.pack();
            frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
            frame.setVisible(true);
            return;
        }
        frame = new JFrame("g1lbertUI");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                if (idle) {
                    exit(0);
                }
            }
        });
        frame.setLayout(new GridBagLayout());
        JLabel title = new JLabel("g1lbertUI");
        title.putClientProperty("FlatLaf.styleClass", "h1");
        frame.add(title, new GridBagConstraints(0, 0, 3, 1, 0, 0, PAGE_START, 0, new Insets(0, 0, 0, 0), 0, 0));
        JLabel subtitle = new JLabel("A GUI for g1lbertJB.");
        subtitle.putClientProperty("FlatLaf.styleClass", "h2");
        frame.add(subtitle, new GridBagConstraints(0, 1, 3, 1, 0, 0, PAGE_START, 0, new Insets(0, 0, 0, 0), 0, 0));
        status = new JLabel("Not started yet.");
        frame.add(status, new GridBagConstraints(0, 3, 3, 1, 0, 0, PAGE_START, 0, new Insets(0, 0, 0, 0), 0, 0));
        substatus = new JLabel("Click the button to start.");
        frame.add(substatus, new GridBagConstraints(0, 4, 3, 1, 0, 0, PAGE_START, 0, new Insets(0, 0, 0, 0), 0, 0));
        JButton doIt = new JButton("Do it!");
        doIt.addActionListener(e ->
            new Thread(() -> {
                if (SystemUtils.IS_OS_MAC_OSX) {
                    runG1lbert("gilbertjb_macos");
                }
            }).start());
        new Thread(() -> {
            while (true) {
                doIt.setEnabled(idle && iosDeviceConnected());
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    System.err.println(e);
                }
            }
        }).start();
        frame.add(doIt, new GridBagConstraints(1, 2, 1, 1, 0, 0, PAGE_START, 0, new Insets(0, 0, 0, 0), 0, 0));
        frame.add(new JLabel(), new GridBagConstraints(0, 2, 1, 1, 1, 0, PAGE_START, 0, new Insets(0, 0, 0, 0), 0, 0));
        frame.add(new JLabel(), new GridBagConstraints(2, 2, 1, 1, 1, 0, PAGE_START, 0, new Insets(0, 0, 0, 0), 0, 0));
        frame.pack();
        {
            Dimension dim = frame.getSize();
            frame.setSize(dim.width + 128, dim.height + 16);
        }
        frame.setVisible(true);
    }
    
    private static Pattern buildDetectPattern = Pattern.compile("Device is a .*? with build .*?");
    
    private static String getLocalPath() {
        try {
            return new File(Main.class.getProtectionDomain().getCodeSource().getLocation()
                    .toURI()).getParent() + "/";
        } catch (URISyntaxException e) {
            return "./";
        }
    }
    
    private static void runG1lbert(String commandName) {
        idle = false;
        Runtime runtime = Runtime.getRuntime();
        try {
            Process process = runtime.exec(getLocalPath() + commandName);
            InputStream stream = process.getInputStream();
            String line = "";
            while(process.isAlive() || (stream.available() != 0)) {
                for (byte readByte : stream.readNBytes(16)) {
                    char read = (char) readByte;
                    if (read == '\n') {
                        System.out.println(line);
                        if (line.contains("[error]")) {
                            if (line.contains("Device already jailbroken!")) {
                                substatus.setText("Already jailbroken, no need to do anything.");
                            } else if (line.contains("Failed to open directory \"payload")) {
                                substatus.setText("Couldn't find device payload, is it supported?");
                            } else if (line.contains("No device found")) {
                                status.setText("An error occurred!");
                                substatus.setText("No iOS device is plugged in.");
                            } else if (line.contains("lockdownd")) {
                                status.setText("An error occurred!");
                                substatus.setText("Please unlock your device.");
                            } else if (line.contains("Could not get device information")) {
                                status.setText("An error occurred!");
                                substatus.setText("Failed to retrieve device information.");
                            }
                        } else if (line.contains("[debug]")) {
                            if (buildDetectPattern.matcher(line).find()) {
                                String[] lineSplit = line.split(" ");
                                status.setText("Found a " + lineSplit[4] + " on " + lineSplit[7] + "!");
                            }
                        }
                        line = "";
                    } else {
                        line += read;
                    }
                }
            }
            idle = true;
        } catch (IOException e) {
            status.setText("An error occurred!");
            substatus.setText("Couldn't run command: ./" + commandName);
            idle = true;
        }
    }
}