package ca.litten.g1lbertUI;

import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.themes.FlatMacDarkLaf;
import org.zeroturnaround.exec.ProcessExecutor;
import org.zeroturnaround.exec.stream.LogOutputStream;
import oshi.*;
import oshi.hardware.UsbDevice;
import org.apache.commons.lang3.SystemUtils;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.List;
import java.util.regex.*;

import static java.awt.GridBagConstraints.*;
import static java.lang.System.exit;

public class Main {
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
        if (SystemUtils.IS_OS_MAC_OSX) {
            System.setProperty("apple.laf.useScreenMenuBar", "true");
            System.setProperty("apple.awt.application.name", "g1lbertUI");
            System.setProperty("apple.awt.application.appearance", "NSAppearanceNameDarkAqua");
            FlatMacDarkLaf.setup();
        } else {
            FlatDarculaLaf.setup();
        }
        JFrame frame;
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
                System.out.println(SystemUtils.OS_ARCH);
                if (SystemUtils.IS_OS_MAC_OSX) {
                    runG1lbert("gilbertjb_macos");
                } else if (SystemUtils.IS_OS_LINUX) {
                    if (SystemUtils.OS_ARCH.equals("x86_64")) {
                        runG1lbert("gilbertjb_linux_x86_64");
                    } else if (SystemUtils.OS_ARCH.contains("arm64")) {
                        runG1lbert("gilbertjb_linux_arm64");
                    } else if (SystemUtils.OS_ARCH.contains("arm")) {
                        runG1lbert("gilbertjb_linux_armhf");
                    } else {
                        runG1lbert("gilbertjb_linux");
                    }
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
    
    private static final Pattern buildDetectPattern = Pattern.compile("Device is a .*? with build .*?");
    
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
        try {
            new ProcessExecutor().command(getLocalPath() + commandName).redirectErrorStream(true).readOutput(true).redirectOutputAlsoTo(new LogOutputStream() {
                @Override
                protected void processLine(String line) {
                    System.out.println(line);
                    if (line.contains("[error]")) {
                        if (line.contains("Device already jailbroken!")) {
                            SwingUtilities.invokeLater(() -> substatus.setText("Already jailbroken, no need to do anything."));
                        } else if (line.contains("Failed to open directory \"payload")) {
                            SwingUtilities.invokeLater(() -> substatus.setText("Couldn't find device payload, is it supported?"));
                        } else if (line.contains("No device found")) {
                            SwingUtilities.invokeLater(() -> {
                                status.setText("An error occurred!");
                                substatus.setText("No iOS device is plugged in.");
                            });
                        } else if (line.contains("lockdownd")) {
                            SwingUtilities.invokeLater(() -> {
                                status.setText("An error occurred!");
                                substatus.setText("Please unlock your device.");
                            });
                        } else if (line.contains("Could not get device information")) {
                            SwingUtilities.invokeLater(() -> {
                                status.setText("An error occurred!");
                                substatus.setText("Failed to retrieve device information.");
                            });
                        }
                    } else if (line.contains("[debug]")) {
                        if (buildDetectPattern.matcher(line).find()) {
                            String[] lineSplit = line.split(" ");
                            SwingUtilities.invokeLater(() -> status.setText("Found a " + lineSplit[4] + " on " + lineSplit[7] + "!"));
                        }
                    }
                }}).readOutput(true).execute();
            idle = true;
        } catch (Throwable e) {
            status.setText("An error occurred!");
            substatus.setText("Couldn't run command: ./" + commandName);
            idle = true;
        }
    }
}