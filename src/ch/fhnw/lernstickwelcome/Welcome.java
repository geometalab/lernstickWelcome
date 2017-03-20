/*
 * Welcome.java
 *
 * Created on 01.04.2009, 14:11:23
 */
package ch.fhnw.lernstickwelcome;

import ch.fhnw.lernstickwelcome.IPTableEntry.Protocol;
import ch.fhnw.lernstickwelcome.controller.ProcessingException;
import ch.fhnw.lernstickwelcome.controller.TableCellValidationException;
import ch.fhnw.lernstickwelcome.model.WelcomeUtil;
import ch.fhnw.util.LernstickFileTools;
import ch.fhnw.util.MountInfo;
import ch.fhnw.util.Partition;
import ch.fhnw.util.StorageDevice;
import java.applet.Applet;
import java.applet.AudioClip;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Toolkit;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.attribute.PosixFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.text.MessageFormat;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.logging.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.border.TitledBorder;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableColumn;
import javax.swing.text.AbstractDocument;
import org.freedesktop.dbus.exceptions.DBusException;

/**
 * The welcome window of the lernstick
 *
 * @author Ronny Standtke <Ronny.Standtke@gmx.net>
 */
public class Welcome extends javax.swing.JFrame {

    private static final Logger LOGGER
            = Logger.getLogger(Welcome.class.getName());
    private static final ResourceBundle BUNDLE
            = ResourceBundle.getBundle("ch/fhnw/lernstickwelcome/Bundle");
    private final File propertiesFile;
    private final Properties properties;
    private final Toolkit toolkit = Toolkit.getDefaultToolkit();
    private final DefaultListModel menuListModel = new DefaultListModel();
    private final boolean examEnvironment;
    private String fullName;
    private int menuListIndex = 0;
    private StorageDevice systemStorageDevice;
    private Partition exchangePartition;
    private String exchangePartitionLabel;
    private Partition bootConfigPartition;
    private MountInfo bootConfigMountInfo;
    private String aptGetOutput;
    private IPTableModel ipTableModel;
    private MainMenuListEntry firewallEntry;
    private MainMenuListEntry backupEntry;
    private boolean firewallRunning;

    /**
     * Creates new form Welcome
     *
     * @param examEnvironment if <tt>true</tt>, show the version for the exam
     * environment, otherwise for the learning environment
     */
    // XXX Still missing in Backend
    public Welcome(boolean examEnvironment) {
        this.examEnvironment = examEnvironment;

        // log everything...
        Logger globalLogger = Logger.getLogger("ch.fhnw");
        globalLogger.setLevel(Level.ALL);
        SimpleFormatter formatter = new SimpleFormatter();

        // log to console
        ConsoleHandler consoleHandler = new ConsoleHandler();
        consoleHandler.setFormatter(formatter);
        consoleHandler.setLevel(Level.ALL);
        globalLogger.addHandler(consoleHandler);

        // log into a rotating temporaty file of max 5 MB
        try {
            FileHandler fileHandler
                    = new FileHandler("%t/lernstickWelcome", 5000000, 2, true);
            fileHandler.setFormatter(formatter);
            fileHandler.setLevel(Level.ALL);
            globalLogger.addHandler(fileHandler);
        } catch (IOException | SecurityException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
        LOGGER.info("*********** Starting lernstick Welcome ***********");

        initComponents();

        // hide the VirtualBox Extension pack until the following bug is fixed:
        // https://bugs.debian.org/cgi-bin/bugreport.cgi?bug=800034
        virtualBoxCheckBox.setVisible(false);
        virtualBoxLabel.setVisible(false);

        ToolTipManager.sharedInstance().setDismissDelay(60000);
        setBordersEnabled(false);

        // load and apply all properties
        properties = new Properties();
        propertiesFile = new File("/etc/lernstickWelcome");
        try {
            properties.load(new FileInputStream(propertiesFile));
        } catch (IOException ex) {
            LOGGER.log(Level.INFO,
                    "can not load properties from " + propertiesFile, ex);
        }
        backupCheckBox.setSelected("true".equals(
                properties.getProperty(BACKUP)));
        backupSourceTextField.setText(properties.getProperty(
                BACKUP_SOURCE, "/home/user/"));
        backupDirectoryCheckBox.setSelected("true".equals(
                properties.getProperty(BACKUP_DIRECTORY_ENABLED, "true")));
        backupPartitionCheckBox.setSelected("true".equals(
                properties.getProperty(BACKUP_PARTITION_ENABLED)));
        backupPartitionTextField.setText(
                properties.getProperty(BACKUP_PARTITION));
        screenShotCheckBox.setSelected("true".equals(
                properties.getProperty(BACKUP_SCREENSHOT)));
        exchangeAccessCheckBox.setSelected("true".equals(
                properties.getProperty(EXCHANGE_ACCESS)));
        kdePlasmaLockCheckBox.setSelected("true".equals(
                properties.getProperty(KDE_LOCK)));
        allowFilesystemMountCheckbox.setSelected(isFileSystemMountAllowed());

        String frequencyString = properties.getProperty(
                BACKUP_FREQUENCY, "5");
        try {
            backupFrequencySpinner.setValue(new Integer(frequencyString));
        } catch (NumberFormatException ex) {
            LOGGER.log(Level.WARNING,
                    "could not parse backup frequency \"{0}\"",
                    frequencyString);
        }
        readWriteCheckBox.setSelected("true".equals(
                properties.getProperty(SHOW_WELCOME)));
        readOnlyCheckBox.setSelected("true".equals(
                properties.getProperty(SHOW_READ_ONLY_INFO, "true")));

        // XXX GUI
        menuList.setModel(menuListModel);

        menuListModel.addElement(new MainMenuListEntry(
                "/ch/fhnw/lernstickwelcome/icons/messagebox_info.png",
                BUNDLE.getString("Information"), "infoPanel"));
        if (examEnvironment) {
            menuListModel.addElement(new MainMenuListEntry(
                    "/ch/fhnw/lernstickwelcome/icons/32x32/dialog-password.png",
                    BUNDLE.getString("Password"), "passwordChangePanel"));
            firewallEntry = new MainMenuListEntry(
                    "/ch/fhnw/lernstickwelcome/icons/32x32/firewall.png",
                    BUNDLE.getString("Firewall"), "firewallPanel");
            menuListModel.addElement(firewallEntry);
            backupEntry = new MainMenuListEntry(
                    "/ch/fhnw/lernstickwelcome/icons/32x32/backup.png",
                    BUNDLE.getString("Backup"), "backupPanel");
            menuListModel.addElement(backupEntry);
        } else {
            menuListModel.addElement(new MainMenuListEntry(
                    "/ch/fhnw/lernstickwelcome/icons/32x32/copyright.png",
                    BUNDLE.getString("Nonfree_Software"), "nonfreePanel"));
            menuListModel.addElement(new MainMenuListEntry(
                    "/ch/fhnw/lernstickwelcome/icons/32x32/LinuxAdvanced.png",
                    BUNDLE.getString("Teaching_System"), "teachingPanel"));
            menuListModel.addElement(new MainMenuListEntry(
                    "/ch/fhnw/lernstickwelcome/icons/32x32/list-add.png",
                    BUNDLE.getString("Additional_Applications"), "additionalPanel"));
            menuListModel.addElement(new MainMenuListEntry(
                    "/ch/fhnw/lernstickwelcome/icons/32x32/network-server.png",
                    BUNDLE.getString("Proxy"), "proxyPanel"));
            
            
            exchangeAccessCheckBox.setVisible(false);
            exchangeRebootLabel.setVisible(false);
            allowFilesystemMountCheckbox.setVisible(false);

            checkAllPackages();
        }
        menuListModel.addElement(new MainMenuListEntry(
                "/ch/fhnw/lernstickwelcome/icons/32x32/system-run.png",
                BUNDLE.getString("System"), "systemPanel"));
        menuListModel.addElement(new MainMenuListEntry(
                "/ch/fhnw/lernstickwelcome/icons/32x32/partitionmanager.png",
                BUNDLE.getString("Partitions"), "partitionsPanel"));

        menuList.setCellRenderer(new MyListCellRenderer());
        menuList.setSelectedIndex(0);

        AbstractDocument userNameDocument
                = (AbstractDocument) userNameTextField.getDocument();
        userNameDocument.setDocumentFilter(new FullUserNameFilter());
        userNameTextField.setText(systemTask.getUsername().get());

        AbstractDocument exchangePartitionNameDocument
                = (AbstractDocument) exchangePartitionNameTextField.getDocument();
        exchangePartitionNameDocument.setDocumentFilter(
                new DocumentSizeFilter());

        

        if (exchangePartition == null) {
            exchangePartitionNameLabel.setEnabled(false);
            exchangePartitionNameTextField.setEnabled(false);
        } else {
            exchangePartitionLabel = exchangePartition.getIdLabel();
            exchangePartitionNameTextField.setText(exchangePartitionLabel);
        }

        // *** determine some boot config properties ***
        // timeout
        ((JSpinner.DefaultEditor) bootTimeoutSpinner.getEditor()).getTextField().setColumns(2);
        ((JSpinner.DefaultEditor) backupFrequencySpinner.getEditor()).getTextField().setColumns(2);
        try {
            bootTimeoutSpinner.setValue(getTimeout());
        } catch (IOException | DBusException ex) {
            LOGGER.log(Level.WARNING, "could not set boot timeout value", ex);
        }
        updateSecondsLabel();
        
        if (!WelcomeUtil.isImageWritable()) {
            bootTimeoutSpinner.setEnabled(false);
            systemNameTextField.setEditable(false);
            systemVersionTextField.setEditable(false);
        }

        Image image = toolkit.getImage(getClass().getResource(
                "/ch/fhnw/lernstickwelcome/icons/messagebox_info.png"));
        setIconImage(image);

        Color background = UIManager.getDefaults().getColor("Panel.background");
        infoEditorPane.setBackground(background);
        teachingEditorPane.setBackground(background);

        // firewall tables
        ipTableModel = new IPTableModel(firewallIPTable,
                new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
        firewallIPTable.setModel(ipTableModel);
        JComboBox protocolCombobox = new JComboBox();
        protocolCombobox.addItem(Protocol.TCP);
        protocolCombobox.addItem(Protocol.UDP);
        TableColumn protocolColumn
                = firewallIPTable.getColumnModel().getColumn(0);
        protocolColumn.setCellEditor(new DefaultCellEditor(protocolCombobox));
        firewallIPTable.getSelectionModel().addListSelectionListener(
                new ListSelectionListener() {
            @Override
            public void valueChanged(ListSelectionEvent e) {
                if (e.getValueIsAdjusting()) {
                    return;
                }
                int[] selectedRows = firewallIPTable.getSelectedRows();
                boolean selected = selectedRows.length > 0;
                removeIPButton.setEnabled(selected);
                moveUpIPButton.setEnabled(selected && selectedRows[0] > 0);
                moveDownIPButton.setEnabled(selected
                        && (selectedRows[selectedRows.length - 1]
                        < ipTableModel.getRowCount() - 1));
            }
        });

        if (examEnvironment) {
            // firewallTask = new FirewallTask();
        }


        helpTextPane.setCaretPosition(0);

        // fix some size issues
        menuScrollPane.setMinimumSize(menuScrollPane.getPreferredSize());
        infoScrollPane.setMinimumSize(infoScrollPane.getPreferredSize());
        nonfreeLabel.setMinimumSize(nonfreeLabel.getPreferredSize());
        teachingScrollPane.setMinimumSize(
                teachingScrollPane.getPreferredSize());
        Dimension preferredSize = helpScrollPane.getPreferredSize();
        preferredSize.height = 100;
        helpTextPane.setPreferredSize(preferredSize);

        pack();

        // center on screen
        setLocationRelativeTo(null);

        setVisible(true);
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        System.setProperty("awt.useSystemAAFontSettings", "on");
        boolean examEnvironment = false;
        for (String arg : args) {
            if (arg.equals("examEnvironment")) {
                examEnvironment = true;
                break;
            }
        }
        final boolean examEnv = examEnvironment;

        java.awt.EventQueue.invokeLater(new Runnable() {
            @Override
            public void run() {
                new Welcome(examEnv);
            }
        });
    }

    /**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {
        java.awt.GridBagConstraints gridBagConstraints;

        menuScrollPane = new javax.swing.JScrollPane();
        menuList = new javax.swing.JList();
        mainCardPanel = new javax.swing.JPanel();
        infoPanel = new javax.swing.JPanel();
        welcomeLabel = new javax.swing.JLabel();
        infoScrollPane = new javax.swing.JScrollPane();
        infoEditorPane = new javax.swing.JEditorPane();
        passwordChangePanel = new javax.swing.JPanel();
        passwordChangeInfoLabel = new javax.swing.JLabel();
        label1 = new javax.swing.JLabel();
        passwordField1 = new javax.swing.JPasswordField();
        label2 = new javax.swing.JLabel();
        passwordField2 = new javax.swing.JPasswordField();
        passwordChangeButton = new javax.swing.JButton();
        backupPanel = new javax.swing.JPanel();
        backupCheckBox = new javax.swing.JCheckBox();
        backupFrequencyEveryLabel = new javax.swing.JLabel();
        backupFrequencySpinner = new javax.swing.JSpinner();
        backupFrequencyMinuteLabel = new javax.swing.JLabel();
        backupSourcePanel = new javax.swing.JPanel();
        backupSourceLabel = new javax.swing.JLabel();
        backupSourceTextField = new javax.swing.JTextField();
        backupSourceButton = new javax.swing.JButton();
        backupDestinationsPanel = new javax.swing.JPanel();
        backupDirectoryCheckBox = new javax.swing.JCheckBox();
        backupDirectoryLabel = new javax.swing.JLabel();
        backupDirectoryTextField = new javax.swing.JTextField();
        backupDirectoryButton = new javax.swing.JButton();
        backupPartitionCheckBox = new javax.swing.JCheckBox();
        backupPartitionLabel = new javax.swing.JLabel();
        backupPartitionTextField = new javax.swing.JTextField();
        screenShotCheckBox = new javax.swing.JCheckBox();
        nonfreePanel = new javax.swing.JPanel();
        nonfreeLabel = new javax.swing.JLabel();
        recommendedPanel = new javax.swing.JPanel();
        flashCheckBox = new javax.swing.JCheckBox();
        flashLabel = new javax.swing.JLabel();
        readerCheckBox = new javax.swing.JCheckBox();
        readerLabel = new javax.swing.JLabel();
        additionalFontsCheckBox = new javax.swing.JCheckBox();
        fontsLabel = new javax.swing.JLabel();
        multimediaCheckBox = new javax.swing.JCheckBox();
        multimediaLabel = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        miscPanel = new javax.swing.JPanel();
        googleEarthCheckBox = new javax.swing.JCheckBox();
        googleEarthLabel = new javax.swing.JLabel();
        skypeCheckBox = new javax.swing.JCheckBox();
        skypeLabel = new javax.swing.JLabel();
        virtualBoxCheckBox = new javax.swing.JCheckBox();
        virtualBoxLabel = new javax.swing.JLabel();
        jLabel1 = new javax.swing.JLabel();
        fillPanel = new javax.swing.JPanel();
        teachingPanel = new javax.swing.JPanel();
        teachingScrollPane = new javax.swing.JScrollPane();
        teachingEditorPane = new javax.swing.JEditorPane();
        laCheckBox = new javax.swing.JCheckBox();
        laLabel = new javax.swing.JLabel();
        additionalPanel = new javax.swing.JPanel();
        additionalInfoLabel = new javax.swing.JLabel();
        additionalTabbedPane = new javax.swing.JTabbedPane();
        additionalScrollPane = new javax.swing.JScrollPane();
        additionalScrollPanel = new ScrollableJPanel();
        gamesScrollPane = new javax.swing.JScrollPane();
        gamesScrollPanel = new ScrollableJPanel();
        proxyPanel = new javax.swing.JPanel();
        proxyInfoLabel = new javax.swing.JLabel();
        proxyCheckBox = new javax.swing.JCheckBox();
        proxyHostLabel = new javax.swing.JLabel();
        proxyHostTextField = new javax.swing.JTextField();
        proxyPortLabel = new javax.swing.JLabel();
        proxyPortTextField = new javax.swing.JFormattedTextField();
        proxyUserNameLabel = new javax.swing.JLabel();
        proxyUserNameTextField = new javax.swing.JTextField();
        proxyPasswordLabel = new javax.swing.JLabel();
        proxyPasswordField = new javax.swing.JPasswordField();
        systemPanel = new javax.swing.JPanel();
        bootMenuPanel = new javax.swing.JPanel();
        bootTimeoutLabel = new javax.swing.JLabel();
        bootTimeoutSpinner = new javax.swing.JSpinner();
        secondsLabel = new javax.swing.JLabel();
        systemNameLabel = new javax.swing.JLabel();
        systemNameTextField = new javax.swing.JTextField();
        systemVersionLabel = new javax.swing.JLabel();
        systemVersionTextField = new javax.swing.JTextField();
        userNameLabel = new javax.swing.JLabel();
        userNameTextField = new javax.swing.JTextField();
        kdePlasmaLockCheckBox = new javax.swing.JCheckBox();
        noPulseAudioCheckbox = new javax.swing.JCheckBox();
        allowFilesystemMountCheckbox = new javax.swing.JCheckBox();
        jLabel3 = new javax.swing.JLabel();
        partitionsPanel = new javax.swing.JPanel();
        exchangePartitionPanel = new javax.swing.JPanel();
        exchangePartitionNameLabel = new javax.swing.JLabel();
        exchangePartitionNameTextField = new javax.swing.JTextField();
        exchangeAccessCheckBox = new javax.swing.JCheckBox();
        exchangeRebootLabel = new javax.swing.JLabel();
        dataPartitionPanel = new javax.swing.JPanel();
        readWritePanel = new javax.swing.JPanel();
        readWriteCheckBox = new javax.swing.JCheckBox();
        readOnlyPanel = new javax.swing.JPanel();
        readOnlyCheckBox = new javax.swing.JCheckBox();
        firewallPanel = new javax.swing.JPanel();
        firewallInfoLabel = new javax.swing.JLabel();
        firewallStartStopButton = new javax.swing.JButton();
        firewallStatusLabel = new javax.swing.JLabel();
        firewallTabbedPane = new javax.swing.JTabbedPane();
        firewallipv4Panel = new javax.swing.JPanel();
        firewallIPButtonPanel = new javax.swing.JPanel();
        addIPButton = new javax.swing.JButton();
        removeIPButton = new javax.swing.JButton();
        moveUpIPButton = new javax.swing.JButton();
        moveDownIPButton = new javax.swing.JButton();
        firewallIPScrollPane = new javax.swing.JScrollPane();
        firewallIPTable = new javax.swing.JTable();
        firewallURLPanel = new javax.swing.JPanel();
        firewallURLScrollPane = new javax.swing.JScrollPane();
        firewallURLTextArea = new javax.swing.JTextArea();
        helpScrollPane = new javax.swing.JScrollPane();
        helpTextPane = new javax.swing.JTextPane();
        bottomPanel = new javax.swing.JPanel();
        navigaionPanel = new javax.swing.JPanel();
        previousButton = new javax.swing.JButton();
        nextButton = new javax.swing.JButton();
        applyButton = new javax.swing.JButton();
        cancelButton = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.DISPOSE_ON_CLOSE);
        java.util.ResourceBundle bundle = java.util.ResourceBundle.getBundle("ch/fhnw/lernstickwelcome/Bundle"); // NOI18N
        setTitle(bundle.getString("Welcome.title")); // NOI18N
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        addWindowListener(new java.awt.event.WindowAdapter() {
            public void windowOpened(java.awt.event.WindowEvent evt) {
                formWindowOpened(evt);
            }
            public void windowClosed(java.awt.event.WindowEvent evt) {
                formWindowClosed(evt);
            }
        });
        getContentPane().setLayout(new java.awt.GridBagLayout());

        menuList.setSelectionMode(javax.swing.ListSelectionModel.SINGLE_SELECTION);
        menuList.addMouseWheelListener(new java.awt.event.MouseWheelListener() {
            public void mouseWheelMoved(java.awt.event.MouseWheelEvent evt) {
                menuListMouseWheelMoved(evt);
            }
        });
        menuList.addListSelectionListener(new javax.swing.event.ListSelectionListener() {
            public void valueChanged(javax.swing.event.ListSelectionEvent evt) {
                menuListValueChanged(evt);
            }
        });
        menuScrollPane.setViewportView(menuList);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.VERTICAL;
        getContentPane().add(menuScrollPane, gridBagConstraints);

        mainCardPanel.setBorder(javax.swing.BorderFactory.createEtchedBorder());
        mainCardPanel.setLayout(new java.awt.CardLayout());

        infoPanel.setLayout(new java.awt.GridBagLayout());

        welcomeLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/lernstick_usb.png"))); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(20, 10, 0, 0);
        infoPanel.add(welcomeLabel, gridBagConstraints);

        infoScrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        infoEditorPane.setEditable(false);
        infoEditorPane.setContentType(bundle.getString("Welcome.editorPane.contentType")); // NOI18N
        infoEditorPane.setText(bundle.getString("Welcome.infoEditorPane.text")); // NOI18N
        infoEditorPane.addHyperlinkListener(new javax.swing.event.HyperlinkListener() {
            public void hyperlinkUpdate(javax.swing.event.HyperlinkEvent evt) {
                infoEditorPaneHyperlinkUpdate(evt);
            }
        });
        infoScrollPane.setViewportView(infoEditorPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 20, 10);
        infoPanel.add(infoScrollPane, gridBagConstraints);

        mainCardPanel.add(infoPanel, "infoPanel");

        passwordChangePanel.setLayout(new java.awt.GridBagLayout());

        passwordChangeInfoLabel.setText(bundle.getString("Welcome.passwordChangeInfoLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        passwordChangePanel.add(passwordChangeInfoLabel, gridBagConstraints);

        label1.setText(bundle.getString("Welcome.label1.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 0, 0);
        passwordChangePanel.add(label1, gridBagConstraints);

        passwordField1.setColumns(15);
        passwordField1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                passwordField1ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 0, 5);
        passwordChangePanel.add(passwordField1, gridBagConstraints);

        label2.setText(bundle.getString("Welcome.label2.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 0);
        passwordChangePanel.add(label2, gridBagConstraints);

        passwordField2.setColumns(15);
        passwordField2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                passwordField2ActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 5);
        passwordChangePanel.add(passwordField2, gridBagConstraints);

        passwordChangeButton.setText(bundle.getString("Welcome.passwordChangeButton.text")); // NOI18N
        passwordChangeButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                passwordChangeButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        passwordChangePanel.add(passwordChangeButton, gridBagConstraints);

        mainCardPanel.add(passwordChangePanel, "passwordChangePanel");

        backupPanel.setLayout(new java.awt.GridBagLayout());

        backupCheckBox.setText(bundle.getString("Welcome.backupCheckBox.text")); // NOI18N
        backupCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                backupCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        backupPanel.add(backupCheckBox, gridBagConstraints);

        backupFrequencyEveryLabel.setText(bundle.getString("Welcome.backupFrequencyEveryLabel.text")); // NOI18N
        backupFrequencyEveryLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 15, 0, 0);
        backupPanel.add(backupFrequencyEveryLabel, gridBagConstraints);

        backupFrequencySpinner.setModel(new javax.swing.SpinnerNumberModel(1, 1, null, 1));
        backupFrequencySpinner.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 0, 0);
        backupPanel.add(backupFrequencySpinner, gridBagConstraints);

        backupFrequencyMinuteLabel.setText(bundle.getString("Welcome.backupFrequencyMinuteLabel.text")); // NOI18N
        backupFrequencyMinuteLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 3, 0, 0);
        backupPanel.add(backupFrequencyMinuteLabel, gridBagConstraints);

        backupSourcePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.backupSourcePanel.border.title"))); // NOI18N
        backupSourcePanel.setLayout(new java.awt.GridBagLayout());

        backupSourceLabel.setText(bundle.getString("Welcome.backupSourceLabel.text")); // NOI18N
        backupSourceLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 0);
        backupSourcePanel.add(backupSourceLabel, gridBagConstraints);

        backupSourceTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 15, 5, 10);
        backupSourcePanel.add(backupSourceTextField, gridBagConstraints);

        backupSourceButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/document-open-folder.png"))); // NOI18N
        backupSourceButton.setEnabled(false);
        backupSourceButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        backupSourceButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backupSourceButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 5, 10);
        backupSourcePanel.add(backupSourceButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        backupPanel.add(backupSourcePanel, gridBagConstraints);

        backupDestinationsPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.backupDestinationsPanel.border.title"))); // NOI18N
        backupDestinationsPanel.setLayout(new java.awt.GridBagLayout());

        backupDirectoryCheckBox.setText(bundle.getString("Welcome.backupDirectoryCheckBox.text")); // NOI18N
        backupDirectoryCheckBox.setEnabled(false);
        backupDirectoryCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                backupDirectoryCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        backupDestinationsPanel.add(backupDirectoryCheckBox, gridBagConstraints);

        backupDirectoryLabel.setText(bundle.getString("Welcome.backupDirectoryLabel.text")); // NOI18N
        backupDirectoryLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 0, 0);
        backupDestinationsPanel.add(backupDirectoryLabel, gridBagConstraints);

        backupDirectoryTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 0);
        backupDestinationsPanel.add(backupDirectoryTextField, gridBagConstraints);

        backupDirectoryButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/document-open-folder.png"))); // NOI18N
        backupDirectoryButton.setEnabled(false);
        backupDirectoryButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        backupDirectoryButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                backupDirectoryButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(0, 10, 0, 10);
        backupDestinationsPanel.add(backupDirectoryButton, gridBagConstraints);

        backupPartitionCheckBox.setText(bundle.getString("Welcome.backupPartitionCheckBox.text")); // NOI18N
        backupPartitionCheckBox.setEnabled(false);
        backupPartitionCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                backupPartitionCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        backupDestinationsPanel.add(backupPartitionCheckBox, gridBagConstraints);

        backupPartitionLabel.setText(bundle.getString("Welcome.backupPartitionLabel.text")); // NOI18N
        backupPartitionLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(3, 15, 5, 0);
        backupDestinationsPanel.add(backupPartitionLabel, gridBagConstraints);

        backupPartitionTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(3, 10, 5, 0);
        backupDestinationsPanel.add(backupPartitionTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 10, 0, 10);
        backupPanel.add(backupDestinationsPanel, gridBagConstraints);

        screenShotCheckBox.setText(bundle.getString("Welcome.screenShotCheckBox.text")); // NOI18N
        screenShotCheckBox.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 13, 0, 0);
        backupPanel.add(screenShotCheckBox, gridBagConstraints);

        mainCardPanel.add(backupPanel, "backupPanel");

        nonfreePanel.setLayout(new java.awt.GridBagLayout());

        nonfreeLabel.setText(bundle.getString("Welcome.nonfreeLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(20, 10, 0, 10);
        nonfreePanel.add(nonfreeLabel, gridBagConstraints);

        recommendedPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.recommendedPanel.border.title"))); // NOI18N
        recommendedPanel.setLayout(new java.awt.GridBagLayout());

        flashCheckBox.setSelected(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        recommendedPanel.add(flashCheckBox, gridBagConstraints);

        flashLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/Adobe_Flash_cs3.png"))); // NOI18N
        flashLabel.setText(bundle.getString("Welcome.flashLabel.text")); // NOI18N
        flashLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                flashLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        recommendedPanel.add(flashLabel, gridBagConstraints);

        readerCheckBox.setSelected(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        recommendedPanel.add(readerCheckBox, gridBagConstraints);

        readerLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/Adobe_Reader_8_icon.png"))); // NOI18N
        readerLabel.setText(bundle.getString("Welcome.readerLabel.text")); // NOI18N
        readerLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                readerLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        recommendedPanel.add(readerLabel, gridBagConstraints);

        additionalFontsCheckBox.setSelected(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        recommendedPanel.add(additionalFontsCheckBox, gridBagConstraints);

        fontsLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/fonts.png"))); // NOI18N
        fontsLabel.setText(bundle.getString("Welcome.fontsLabel.text")); // NOI18N
        fontsLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                fontsLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        recommendedPanel.add(fontsLabel, gridBagConstraints);

        multimediaCheckBox.setSelected(true);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        recommendedPanel.add(multimediaCheckBox, gridBagConstraints);

        multimediaLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/package_multimedia.png"))); // NOI18N
        multimediaLabel.setText(bundle.getString("Welcome.multimediaLabel.text")); // NOI18N
        multimediaLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                multimediaLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        recommendedPanel.add(multimediaLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        recommendedPanel.add(jLabel2, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(20, 10, 10, 5);
        nonfreePanel.add(recommendedPanel, gridBagConstraints);

        miscPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.miscPanel.border.title"))); // NOI18N
        miscPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        miscPanel.add(googleEarthCheckBox, gridBagConstraints);

        googleEarthLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/googleearth-icon.png"))); // NOI18N
        googleEarthLabel.setText(bundle.getString("Welcome.googleEarthLabel.text")); // NOI18N
        googleEarthLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                googleEarthLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        miscPanel.add(googleEarthLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        miscPanel.add(skypeCheckBox, gridBagConstraints);

        skypeLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/skype.png"))); // NOI18N
        skypeLabel.setText(bundle.getString("Welcome.skypeLabel.text")); // NOI18N
        skypeLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                skypeLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        miscPanel.add(skypeLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        miscPanel.add(virtualBoxCheckBox, gridBagConstraints);

        virtualBoxLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/virtualbox.png"))); // NOI18N
        virtualBoxLabel.setText(bundle.getString("Welcome.virtualBoxLabel.text")); // NOI18N
        virtualBoxLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                virtualBoxLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        miscPanel.add(virtualBoxLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 0, 5, 0);
        miscPanel.add(jLabel1, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(20, 5, 10, 10);
        nonfreePanel.add(miscPanel, gridBagConstraints);

        fillPanel.setLayout(new java.awt.GridBagLayout());
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        nonfreePanel.add(fillPanel, gridBagConstraints);

        mainCardPanel.add(nonfreePanel, "nonfreePanel");

        teachingPanel.setLayout(new java.awt.GridBagLayout());

        teachingScrollPane.setBorder(javax.swing.BorderFactory.createEmptyBorder(1, 1, 1, 1));

        teachingEditorPane.setEditable(false);
        teachingEditorPane.setContentType("text/html"); // NOI18N
        teachingEditorPane.setText(bundle.getString("Welcome.teachingEditorPane.text")); // NOI18N
        teachingEditorPane.addHyperlinkListener(new javax.swing.event.HyperlinkListener() {
            public void hyperlinkUpdate(javax.swing.event.HyperlinkEvent evt) {
                teachingEditorPaneHyperlinkUpdate(evt);
            }
        });
        teachingScrollPane.setViewportView(teachingEditorPane);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        teachingPanel.add(teachingScrollPane, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        teachingPanel.add(laCheckBox, gridBagConstraints);

        laLabel.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/LinuxAdvanced.png"))); // NOI18N
        laLabel.setText(bundle.getString("Welcome.laLabel.text")); // NOI18N
        laLabel.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseClicked(java.awt.event.MouseEvent evt) {
                laLabelMouseClicked(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        teachingPanel.add(laLabel, gridBagConstraints);

        mainCardPanel.add(teachingPanel, "teachingPanel");

        additionalPanel.setLayout(new java.awt.GridBagLayout());

        additionalInfoLabel.setText(bundle.getString("Welcome.additionalInfoLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(20, 10, 0, 10);
        additionalPanel.add(additionalInfoLabel, gridBagConstraints);

        additionalScrollPanel.setLayout(new java.awt.GridBagLayout());
        additionalScrollPane.setViewportView(additionalScrollPanel);

        additionalTabbedPane.addTab(bundle.getString("Welcome.additionalScrollPane.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/applications-other.png")), additionalScrollPane); // NOI18N

        gamesScrollPanel.setLayout(new java.awt.GridBagLayout());
        gamesScrollPane.setViewportView(gamesScrollPanel);

        additionalTabbedPane.addTab(bundle.getString("Welcome.gamesScrollPane.TabConstraints.tabTitle"), new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/32x32/input-gaming.png")), gamesScrollPane); // NOI18N

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        additionalPanel.add(additionalTabbedPane, gridBagConstraints);

        mainCardPanel.add(additionalPanel, "additionalPanel");

        proxyPanel.setLayout(new java.awt.GridBagLayout());

        proxyInfoLabel.setText(bundle.getString("Welcome.proxyInfoLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 0, 10);
        proxyPanel.add(proxyInfoLabel, gridBagConstraints);

        proxyCheckBox.setText(bundle.getString("Welcome.proxyCheckBox.text")); // NOI18N
        proxyCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                proxyCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(10, 11, 0, 11);
        proxyPanel.add(proxyCheckBox, gridBagConstraints);

        proxyHostLabel.setText(bundle.getString("Welcome.proxyHostLabel.text")); // NOI18N
        proxyHostLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 25, 0, 0);
        proxyPanel.add(proxyHostLabel, gridBagConstraints);

        proxyHostTextField.setColumns(20);
        proxyHostTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 10);
        proxyPanel.add(proxyHostTextField, gridBagConstraints);

        proxyPortLabel.setText(bundle.getString("Welcome.proxyPortLabel.text")); // NOI18N
        proxyPortLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 25, 0, 0);
        proxyPanel.add(proxyPortLabel, gridBagConstraints);

        proxyPortTextField.setColumns(5);
        proxyPortTextField.setFormatterFactory(new javax.swing.text.DefaultFormatterFactory(new javax.swing.text.NumberFormatter(new java.text.DecimalFormat("#####"))));
        proxyPortTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 10);
        proxyPanel.add(proxyPortTextField, gridBagConstraints);

        proxyUserNameLabel.setText(bundle.getString("Welcome.proxyUserNameLabel.text")); // NOI18N
        proxyUserNameLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 25, 0, 0);
        proxyPanel.add(proxyUserNameLabel, gridBagConstraints);

        proxyUserNameTextField.setColumns(20);
        proxyUserNameTextField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 0, 10);
        proxyPanel.add(proxyUserNameTextField, gridBagConstraints);

        proxyPasswordLabel.setText(bundle.getString("Welcome.proxyPasswordLabel.text")); // NOI18N
        proxyPasswordLabel.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 25, 10, 0);
        proxyPanel.add(proxyPasswordLabel, gridBagConstraints);

        proxyPasswordField.setColumns(20);
        proxyPasswordField.setEnabled(false);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(3, 5, 10, 10);
        proxyPanel.add(proxyPasswordField, gridBagConstraints);

        mainCardPanel.add(proxyPanel, "proxyPanel");

        systemPanel.setLayout(new java.awt.GridBagLayout());

        bootMenuPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.bootMenuPanel.border.title"))); // NOI18N
        bootMenuPanel.setLayout(new java.awt.GridBagLayout());

        bootTimeoutLabel.setText(bundle.getString("Welcome.bootTimeoutLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        bootMenuPanel.add(bootTimeoutLabel, gridBagConstraints);

        bootTimeoutSpinner.setModel(new javax.swing.SpinnerNumberModel(10, 1, null, 1));
        bootTimeoutSpinner.addChangeListener(new javax.swing.event.ChangeListener() {
            public void stateChanged(javax.swing.event.ChangeEvent evt) {
                bootTimeoutSpinnerStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        bootMenuPanel.add(bootTimeoutSpinner, gridBagConstraints);

        secondsLabel.setText(bundle.getString("Welcome.secondsLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 3, 0, 5);
        bootMenuPanel.add(secondsLabel, gridBagConstraints);

        systemNameLabel.setText(bundle.getString("Welcome.systemNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 0);
        bootMenuPanel.add(systemNameLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        bootMenuPanel.add(systemNameTextField, gridBagConstraints);

        systemVersionLabel.setText(bundle.getString("Welcome.systemVersionLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 0);
        bootMenuPanel.add(systemVersionLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 5, 5);
        bootMenuPanel.add(systemVersionTextField, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(20, 10, 0, 10);
        systemPanel.add(bootMenuPanel, gridBagConstraints);

        userNameLabel.setText(bundle.getString("Welcome.userNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(25, 10, 0, 0);
        systemPanel.add(userNameLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(25, 10, 0, 10);
        systemPanel.add(userNameTextField, gridBagConstraints);

        kdePlasmaLockCheckBox.setText(bundle.getString("Welcome.kdePlasmaLockCheckBox.text")); // NOI18N
        kdePlasmaLockCheckBox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                kdePlasmaLockCheckBoxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(10, 6, 0, 0);
        systemPanel.add(kdePlasmaLockCheckBox, gridBagConstraints);

        noPulseAudioCheckbox.setText(bundle.getString("Welcome.noPulseAudioCheckbox.text")); // NOI18N
        noPulseAudioCheckbox.setToolTipText(bundle.getString("Welcome.noPulseAudioCheckbox.toolTipText")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        systemPanel.add(noPulseAudioCheckbox, gridBagConstraints);

        allowFilesystemMountCheckbox.setText(bundle.getString("Welcome.allowFilesystemMountCheckbox.text")); // NOI18N
        allowFilesystemMountCheckbox.addItemListener(new java.awt.event.ItemListener() {
            public void itemStateChanged(java.awt.event.ItemEvent evt) {
                allowFilesystemMountCheckboxItemStateChanged(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.FIRST_LINE_START;
        gridBagConstraints.insets = new java.awt.Insets(0, 6, 0, 0);
        systemPanel.add(allowFilesystemMountCheckbox, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weighty = 1.0;
        systemPanel.add(jLabel3, gridBagConstraints);

        mainCardPanel.add(systemPanel, "systemPanel");

        partitionsPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.exchangePartitionPanel.border.title"))); // NOI18N
        exchangePartitionPanel.setLayout(new java.awt.GridBagLayout());

        exchangePartitionNameLabel.setText(bundle.getString("Welcome.exchangePartitionNameLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 3, 0);
        exchangePartitionPanel.add(exchangePartitionNameLabel, gridBagConstraints);
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 10, 3, 10);
        exchangePartitionPanel.add(exchangePartitionNameTextField, gridBagConstraints);

        exchangeAccessCheckBox.setText(bundle.getString("Welcome.exchangeAccessCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(7, 7, 0, 0);
        exchangePartitionPanel.add(exchangeAccessCheckBox, gridBagConstraints);

        exchangeRebootLabel.setText(bundle.getString("Welcome.exchangeRebootLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(0, 15, 5, 10);
        exchangePartitionPanel.add(exchangeRebootLabel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTHWEST;
        gridBagConstraints.insets = new java.awt.Insets(20, 0, 0, 0);
        partitionsPanel.add(exchangePartitionPanel, gridBagConstraints);

        dataPartitionPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.dataPartitionPanel.border.title"))); // NOI18N
        dataPartitionPanel.setLayout(new java.awt.GridBagLayout());

        readWritePanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.readWritePanel.border.title"))); // NOI18N
        readWritePanel.setLayout(new java.awt.GridBagLayout());

        readWriteCheckBox.setText(bundle.getString("Welcome.readWriteCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        readWritePanel.add(readWriteCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        dataPartitionPanel.add(readWritePanel, gridBagConstraints);

        readOnlyPanel.setBorder(javax.swing.BorderFactory.createTitledBorder(bundle.getString("Welcome.readOnlyPanel.border.title"))); // NOI18N
        readOnlyPanel.setLayout(new java.awt.GridBagLayout());

        readOnlyCheckBox.setText(bundle.getString("Welcome.readOnlyCheckBox.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        readOnlyPanel.add(readOnlyCheckBox, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 5, 5);
        dataPartitionPanel.add(readOnlyPanel, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        partitionsPanel.add(dataPartitionPanel, gridBagConstraints);

        mainCardPanel.add(partitionsPanel, "partitionsPanel");

        firewallPanel.setLayout(new java.awt.GridBagLayout());

        firewallInfoLabel.setText(bundle.getString("Welcome.firewallInfoLabel.text")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(15, 5, 0, 5);
        firewallPanel.add(firewallInfoLabel, gridBagConstraints);

        firewallStartStopButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/start.png"))); // NOI18N
        firewallStartStopButton.setToolTipText(bundle.getString("Firewall_toolTip_start")); // NOI18N
        firewallStartStopButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        firewallStartStopButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                firewallStartStopButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        firewallPanel.add(firewallStartStopButton, gridBagConstraints);

        firewallStatusLabel.setForeground(java.awt.Color.red);
        firewallStatusLabel.setLabelFor(firewallStartStopButton);
        firewallStatusLabel.setText(bundle.getString("Welcome.firewallStatusLabel.text_stopped")); // NOI18N
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.insets = new java.awt.Insets(5, 5, 0, 5);
        firewallPanel.add(firewallStatusLabel, gridBagConstraints);

        firewallipv4Panel.setLayout(new java.awt.GridBagLayout());

        firewallIPButtonPanel.setLayout(new java.awt.GridBagLayout());

        addIPButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/list-add.png"))); // NOI18N
        addIPButton.setToolTipText(bundle.getString("Welcome.addIPButton.toolTipText")); // NOI18N
        addIPButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        addIPButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                addIPButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        firewallIPButtonPanel.add(addIPButton, gridBagConstraints);

        removeIPButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/list-remove.png"))); // NOI18N
        removeIPButton.setToolTipText(bundle.getString("Welcome.removeIPButton.toolTipText")); // NOI18N
        removeIPButton.setEnabled(false);
        removeIPButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        removeIPButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                removeIPButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        firewallIPButtonPanel.add(removeIPButton, gridBagConstraints);

        moveUpIPButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/arrow-up.png"))); // NOI18N
        moveUpIPButton.setToolTipText(bundle.getString("Welcome.moveUpIPButton.toolTipText")); // NOI18N
        moveUpIPButton.setEnabled(false);
        moveUpIPButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moveUpIPButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveUpIPButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        firewallIPButtonPanel.add(moveUpIPButton, gridBagConstraints);

        moveDownIPButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/arrow-down.png"))); // NOI18N
        moveDownIPButton.setToolTipText(bundle.getString("Welcome.moveDownIPButton.toolTipText")); // NOI18N
        moveDownIPButton.setEnabled(false);
        moveDownIPButton.setMargin(new java.awt.Insets(2, 2, 2, 2));
        moveDownIPButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                moveDownIPButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.insets = new java.awt.Insets(5, 0, 0, 0);
        firewallIPButtonPanel.add(moveDownIPButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.NORTH;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(20, 5, 0, 0);
        firewallipv4Panel.add(firewallIPButtonPanel, gridBagConstraints);

        firewallIPScrollPane.setViewportView(firewallIPTable);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        firewallipv4Panel.add(firewallIPScrollPane, gridBagConstraints);

        firewallTabbedPane.addTab(bundle.getString("Welcome.firewallipv4Panel.TabConstraints.tabTitle"), firewallipv4Panel); // NOI18N

        firewallURLPanel.setLayout(new java.awt.GridBagLayout());

        firewallURLTextArea.setColumns(20);
        firewallURLTextArea.setRows(5);
        firewallURLScrollPane.setViewportView(firewallURLTextArea);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        firewallURLPanel.add(firewallURLScrollPane, gridBagConstraints);

        firewallTabbedPane.addTab(bundle.getString("Welcome.firewallURLPanel.TabConstraints.tabTitle"), firewallURLPanel); // NOI18N

        helpTextPane.setEditable(false);
        helpTextPane.setContentType("text/html"); // NOI18N
        helpTextPane.setText(bundle.getString("Welcome.helpTextPane.text")); // NOI18N
        helpScrollPane.setViewportView(helpTextPane);

        firewallTabbedPane.addTab(bundle.getString("Welcome.helpScrollPane.TabConstraints.tabTitle"), helpScrollPane); // NOI18N

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        gridBagConstraints.insets = new java.awt.Insets(10, 0, 0, 0);
        firewallPanel.add(firewallTabbedPane, gridBagConstraints);

        mainCardPanel.add(firewallPanel, "firewallPanel");

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.BOTH;
        gridBagConstraints.weightx = 1.0;
        gridBagConstraints.weighty = 1.0;
        getContentPane().add(mainCardPanel, gridBagConstraints);

        bottomPanel.setLayout(new java.awt.GridBagLayout());

        navigaionPanel.setLayout(new java.awt.GridLayout(1, 0, 5, 0));

        previousButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/go-previous.png"))); // NOI18N
        previousButton.setText(bundle.getString("Welcome.previousButton.text")); // NOI18N
        previousButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                previousButtonActionPerformed(evt);
            }
        });
        navigaionPanel.add(previousButton);

        nextButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/go-next.png"))); // NOI18N
        nextButton.setText(bundle.getString("Welcome.nextButton.text")); // NOI18N
        nextButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                nextButtonActionPerformed(evt);
            }
        });
        navigaionPanel.add(nextButton);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.anchor = java.awt.GridBagConstraints.WEST;
        gridBagConstraints.weightx = 1.0;
        bottomPanel.add(navigaionPanel, gridBagConstraints);

        applyButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/16x16/dialog-ok-apply.png"))); // NOI18N
        applyButton.setText(bundle.getString("Welcome.applyButton.text")); // NOI18N
        applyButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                applyButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.anchor = java.awt.GridBagConstraints.EAST;
        bottomPanel.add(applyButton, gridBagConstraints);

        cancelButton.setIcon(new javax.swing.ImageIcon(getClass().getResource("/ch/fhnw/lernstickwelcome/icons/exit.png"))); // NOI18N
        cancelButton.setText(bundle.getString("Welcome.cancelButton.text")); // NOI18N
        cancelButton.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                cancelButtonActionPerformed(evt);
            }
        });
        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(0, 5, 0, 0);
        bottomPanel.add(cancelButton, gridBagConstraints);

        gridBagConstraints = new java.awt.GridBagConstraints();
        gridBagConstraints.gridwidth = java.awt.GridBagConstraints.REMAINDER;
        gridBagConstraints.fill = java.awt.GridBagConstraints.HORIZONTAL;
        gridBagConstraints.insets = new java.awt.Insets(10, 5, 10, 10);
        getContentPane().add(bottomPanel, gridBagConstraints);
    }// </editor-fold>//GEN-END:initComponents

    private void googleEarthLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_googleEarthLabelMouseClicked
        toggleCheckBox(googleEarthCheckBox);
}//GEN-LAST:event_googleEarthLabelMouseClicked

    private void skypeLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_skypeLabelMouseClicked
        toggleCheckBox(skypeCheckBox);
    }//GEN-LAST:event_skypeLabelMouseClicked

    private void flashLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_flashLabelMouseClicked
        toggleCheckBox(flashCheckBox);
    }//GEN-LAST:event_flashLabelMouseClicked

    private void fontsLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_fontsLabelMouseClicked
        toggleCheckBox(additionalFontsCheckBox);
    }//GEN-LAST:event_fontsLabelMouseClicked

    private void applyButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_applyButtonActionPerformed
        try {
            apply();
        } catch (DBusException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
    }//GEN-LAST:event_applyButtonActionPerformed

    private void cancelButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_cancelButtonActionPerformed
        if ((bootConfigMountInfo != null)
                && (!bootConfigMountInfo.alreadyMounted())) {
            try {
                bootConfigPartition.umount();
            } catch (DBusException ex) {
                LOGGER.log(Level.SEVERE, null, ex);
            }
        }
        System.exit(0);
    }//GEN-LAST:event_cancelButtonActionPerformed

    private void multimediaLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_multimediaLabelMouseClicked
        toggleCheckBox(multimediaCheckBox);
}//GEN-LAST:event_multimediaLabelMouseClicked

    private void infoEditorPaneHyperlinkUpdate(javax.swing.event.HyperlinkEvent evt) {//GEN-FIRST:event_infoEditorPaneHyperlinkUpdate
        openLinkInBrowser(evt);
    }//GEN-LAST:event_infoEditorPaneHyperlinkUpdate

    private void readerLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_readerLabelMouseClicked
        toggleCheckBox(readerCheckBox);
    }//GEN-LAST:event_readerLabelMouseClicked

    private void formWindowClosed(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowClosed
        LOGGER.info("exiting program");
        System.exit(0);
    }//GEN-LAST:event_formWindowClosed

    private void proxyCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_proxyCheckBoxItemStateChanged
        setProxyEnabled(proxyCheckBox.isSelected());
    }//GEN-LAST:event_proxyCheckBoxItemStateChanged

    private void laLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_laLabelMouseClicked
        toggleCheckBox(laCheckBox);
    }//GEN-LAST:event_laLabelMouseClicked

    private void menuListValueChanged(javax.swing.event.ListSelectionEvent evt) {//GEN-FIRST:event_menuListValueChanged
        if (evt.getValueIsAdjusting()) {
            return;
        }

        int selectedIndex = menuList.getSelectedIndex();
        if (selectedIndex == -1) {
            menuList.setSelectedIndex(menuListIndex);
            return;
        } else {
            menuListIndex = selectedIndex;
        }

        MainMenuListEntry entry
                = (MainMenuListEntry) menuList.getSelectedValue();
        selectCard(entry.getPanelID());

        previousButton.setEnabled(selectedIndex > 0);
        nextButton.setEnabled(
                (selectedIndex + 1) < menuList.getModel().getSize());
    }//GEN-LAST:event_menuListValueChanged

    private void menuListMouseWheelMoved(java.awt.event.MouseWheelEvent evt) {//GEN-FIRST:event_menuListMouseWheelMoved
        if (evt.getWheelRotation() < 0) {
            if (menuListIndex > 0) {
                menuList.setSelectedIndex(menuListIndex - 1);
            }
        } else if (menuListIndex < (menuListModel.getSize() - 1)) {
            menuList.setSelectedIndex(menuListIndex + 1);
        }
    }//GEN-LAST:event_menuListMouseWheelMoved

    private void nextButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_nextButtonActionPerformed
        menuList.setSelectedIndex(menuListIndex + 1);
    }//GEN-LAST:event_nextButtonActionPerformed

    private void previousButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_previousButtonActionPerformed
        menuList.setSelectedIndex(menuListIndex - 1);
    }//GEN-LAST:event_previousButtonActionPerformed

    private void teachingEditorPaneHyperlinkUpdate(javax.swing.event.HyperlinkEvent evt) {//GEN-FIRST:event_teachingEditorPaneHyperlinkUpdate
        openLinkInBrowser(evt);
    }//GEN-LAST:event_teachingEditorPaneHyperlinkUpdate

    private void bootTimeoutSpinnerStateChanged(javax.swing.event.ChangeEvent evt) {//GEN-FIRST:event_bootTimeoutSpinnerStateChanged
        updateSecondsLabel();
    }//GEN-LAST:event_bootTimeoutSpinnerStateChanged

    private void formWindowOpened(java.awt.event.WindowEvent evt) {//GEN-FIRST:event_formWindowOpened
        // enforce visibility of top applications
        additionalScrollPane.getVerticalScrollBar().setValue(0);
        gamesScrollPane.getVerticalScrollBar().setValue(0);
    }//GEN-LAST:event_formWindowOpened

    private void passwordChangeButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_passwordChangeButtonActionPerformed
        changePassword();
    }//GEN-LAST:event_passwordChangeButtonActionPerformed

    private void passwordField1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_passwordField1ActionPerformed
        passwordField2.selectAll();
        passwordField2.requestFocusInWindow();
    }//GEN-LAST:event_passwordField1ActionPerformed

    private void passwordField2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_passwordField2ActionPerformed
        changePassword();
    }//GEN-LAST:event_passwordField2ActionPerformed

    private void addIPButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_addIPButtonActionPerformed
        ipTableModel.addEntry();
    }//GEN-LAST:event_addIPButtonActionPerformed

    private void removeIPButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_removeIPButtonActionPerformed
        TableCellEditor editor = firewallIPTable.getCellEditor();
        if (editor != null) {
            editor.stopCellEditing();
        }
        int[] selectedRows = firewallIPTable.getSelectedRows();
        for (int i = selectedRows.length - 1; i >= 0; i--) {
            ipTableModel.removeRow(selectedRows[i]);
        }
    }//GEN-LAST:event_removeIPButtonActionPerformed

    private void moveUpIPButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveUpIPButtonActionPerformed
        ipTableModel.moveEntries(true);
    }//GEN-LAST:event_moveUpIPButtonActionPerformed

    private void moveDownIPButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_moveDownIPButtonActionPerformed
        ipTableModel.moveEntries(false);
    }//GEN-LAST:event_moveDownIPButtonActionPerformed

    private void backupCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_backupCheckBoxItemStateChanged
        setBackupEnabled(backupCheckBox.isSelected());
    }//GEN-LAST:event_backupCheckBoxItemStateChanged

    private void backupSourceButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backupSourceButtonActionPerformed
        showFileSelector(backupSourceTextField);
    }//GEN-LAST:event_backupSourceButtonActionPerformed

    private void backupDirectoryButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_backupDirectoryButtonActionPerformed
        showFileSelector(backupDirectoryTextField);
    }//GEN-LAST:event_backupDirectoryButtonActionPerformed

    private void backupDirectoryCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_backupDirectoryCheckBoxItemStateChanged
        updateBackupDirectoryEnabled();
    }//GEN-LAST:event_backupDirectoryCheckBoxItemStateChanged

    private void backupPartitionCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_backupPartitionCheckBoxItemStateChanged
        updateBackupPartitionEnabled();
    }//GEN-LAST:event_backupPartitionCheckBoxItemStateChanged

    private void kdePlasmaLockCheckBoxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_kdePlasmaLockCheckBoxItemStateChanged
        if (!kdePlasmaLockCheckBox.isSelected()) {
            try {
                PosixFileAttributes attributes = Files.readAttributes(
                        APPLETS_CONFIG_FILE, PosixFileAttributes.class
                );
                Set<PosixFilePermission> permissions = attributes.permissions();

                permissions.add(PosixFilePermission.OWNER_WRITE);

                Files.setPosixFilePermissions(APPLETS_CONFIG_FILE, permissions);
            } catch (IOException iOException) {
                LOGGER.log(Level.WARNING, "", iOException);
            }
        }
    }//GEN-LAST:event_kdePlasmaLockCheckBoxItemStateChanged

    private void firewallStartStopButtonActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_firewallStartStopButtonActionPerformed
        toggleFirewallState();
    }//GEN-LAST:event_firewallStartStopButtonActionPerformed

    private void virtualBoxLabelMouseClicked(java.awt.event.MouseEvent evt) {//GEN-FIRST:event_virtualBoxLabelMouseClicked
        toggleCheckBox(virtualBoxCheckBox);
    }//GEN-LAST:event_virtualBoxLabelMouseClicked

    private void allowFilesystemMountCheckboxItemStateChanged(java.awt.event.ItemEvent evt) {//GEN-FIRST:event_allowFilesystemMountCheckboxItemStateChanged
        try {
            if (allowFilesystemMountCheckbox.isSelected()) {
                LernstickFileTools.replaceText(PKLA_PATH.toString(),
                        Pattern.compile("=auth_self"), "=yes");
            } else {
                LernstickFileTools.replaceText(PKLA_PATH.toString(),
                        Pattern.compile("=yes"), "=auth_self");
            }
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "", ex);
        }
    }//GEN-LAST:event_allowFilesystemMountCheckboxItemStateChanged

    private boolean isFileSystemMountAllowed() {
        try {
            List<String> pklaRules
                    = LernstickFileTools.readFile(PKLA_PATH.toFile());
            for (String pklaRule : pklaRules) {
                if (pklaRule.equals("ResultAny=yes")) {
                    return true;
                }
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, "", ex);
        }
        return false;
    }
///////////////////////////////////////////////

    private void toggleFirewallState() {
        String action = firewallRunning ? "stop" : "start";
        int ret = PROCESS_EXECUTOR.executeProcess(
                true, true, "lernstick-firewall", action);

        if (ret == 0) {
            firewallRunning = !firewallRunning;
            // update widget
            updateFirewallState();
        } else {
            LOGGER.log(Level.WARNING,
                    action + "ing lernstick-firewall failed, return code {0} "
                    + "stdout: '{1}', stderr: '{2}'",
                    new Object[]{
                        ret,
                        PROCESS_EXECUTOR.getStdOut(),
                        PROCESS_EXECUTOR.getStdErr()
                    });
            String messageId = firewallRunning
                    ? "Stop_firewall_error"
                    : "Start_firewall_error";
            JOptionPane.showMessageDialog(this,
                    BUNDLE.getString(messageId),
                    BUNDLE.getString("Error"),
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // XXX GUI (Backend was added)
    private void updateFirewallState() {
        // check firewall state
        
        boolean firewallRunning = firewallTask.updateFirewallState() == 0;

        // update button icon
        String iconBasePath = "/ch/fhnw/lernstickwelcome/icons/16x16/";
        String iconPath = firewallRunning
                ? iconBasePath + "stop.png"
                : iconBasePath + "start.png";
        firewallStartStopButton.setIcon(
                new ImageIcon(getClass().getResource(iconPath)));
        String tooltipString = firewallRunning
                ? BUNDLE.getString("Firewall_toolTip_stop")
                : BUNDLE.getString("Firewall_toolTip_start");
        firewallStartStopButton.setToolTipText(tooltipString);

        // update label text and color
        String labelString = firewallRunning
                ? BUNDLE.getString("Welcome.firewallStatusLabel.text_running")
                : BUNDLE.getString("Welcome.firewallStatusLabel.text_stopped");
        firewallStatusLabel.setText(labelString);
        firewallStatusLabel.setForeground(firewallRunning
                ? Color.green
                : Color.red);
    }

    private void getFullUserName() {
        AbstractDocument userNameDocument
                = (AbstractDocument) userNameTextField.getDocument();
        userNameDocument.setDocumentFilter(new FullUserNameFilter());
        
        userNameTextField.setText(systemTask.getFullUserName());
    }

    private void showFileSelector(JTextField textField) {
        UIManager.put("FileChooser.readOnly", Boolean.TRUE);
        File selectedDirectory = new File(textField.getText());
        JFileChooser fileChooser
                = new JFileChooser(selectedDirectory.getParent());
        fileChooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
        fileChooser.setSelectedFile(selectedDirectory);
        fileChooser.showOpenDialog(this);
        selectedDirectory = fileChooser.getSelectedFile();
        textField.setText(selectedDirectory.getPath());
    }

    private void setBackupEnabled(boolean enabled) {
        backupSourceLabel.setEnabled(enabled);
        backupSourceTextField.setEnabled(enabled);
        backupSourceButton.setEnabled(enabled);

        backupDirectoryCheckBox.setEnabled(enabled);
        updateBackupDirectoryEnabled();

        backupPartitionCheckBox.setEnabled(enabled);
        updateBackupPartitionEnabled();

        screenShotCheckBox.setEnabled(enabled);

        backupFrequencyEveryLabel.setEnabled(enabled);
        backupFrequencySpinner.setEnabled(enabled);
        backupFrequencyMinuteLabel.setEnabled(enabled);

        setBordersEnabled(enabled);
    }

    private void updateBackupDirectoryEnabled() {
        boolean enabled = backupCheckBox.isSelected()
                && backupDirectoryCheckBox.isSelected();
        backupDirectoryLabel.setEnabled(enabled);
        backupDirectoryTextField.setEnabled(enabled);
        backupDirectoryButton.setEnabled(enabled);
    }

    private void updateBackupPartitionEnabled() {
        boolean enabled = backupCheckBox.isSelected()
                && backupPartitionCheckBox.isSelected();
        backupPartitionLabel.setEnabled(enabled);
        backupPartitionTextField.setEnabled(enabled);
    }

    private void setBordersEnabled(boolean enabled) {
        Color color = enabled ? Color.BLACK : Color.GRAY;
        TitledBorder border = (TitledBorder) backupSourcePanel.getBorder();
        border.setTitleColor(color);
        border = (TitledBorder) backupDestinationsPanel.getBorder();
        border.setTitleColor(color);
        backupSourcePanel.repaint();
        backupDestinationsPanel.repaint();
    }

    private void parseURLWhiteList() throws IOException {
        try (FileReader fileReader = new FileReader(URL_WHITELIST_FILENAME);
                BufferedReader bufferedReader = new BufferedReader(fileReader)) {
            StringBuilder builder = new StringBuilder();
            for (String line = bufferedReader.readLine(); line != null;) {
                builder.append(line);
                builder.append('\n');
                line = bufferedReader.readLine();
            }
            firewallURLTextArea.setText(builder.toString());
        }
    }

    private void parseNetWhiteList() throws IOException {
        // TODO
        ipTableModel.fireTableDataChanged();
    }

    private void changePassword() {
        try {
            systemTask.changePassword();
        } catch(ProcessingException ex) {
            JOptionPane.showMessageDialog(this,
                    BUNDLE.getString(ex.getMessage()),
                    BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE);
        }
    }

    private int getTimeout() throws IOException, DBusException {

        // use syslinux configuration as reference for the timeout setting
        List<File> syslinuxConfigFiles;
        if (bootConfigPartition == null) {
            // legacy system
            syslinuxConfigFiles = getSyslinuxConfigFiles(
                    new File(IMAGE_DIRECTORY));
        } else {
            // system with a separate boot partition
            syslinuxConfigFiles = bootConfigPartition.executeMounted(
                    new Partition.Action<List<File>>() {
                @Override
                public List<File> execute(File mountPath) {
                    return getSyslinuxConfigFiles(mountPath);
                }
            });
        }

        Pattern timeoutPattern = Pattern.compile("timeout (.*)");
        for (File syslinuxConfigFile : syslinuxConfigFiles) {
            List<String> configFileLines = readFile(syslinuxConfigFile);
            for (String configFileLine : configFileLines) {
                Matcher matcher = timeoutPattern.matcher(configFileLine);
                if (matcher.matches()) {
                    String timeoutString = matcher.group(1);
                    try {
                        return Integer.parseInt(timeoutString) / 10;
                    } catch (NumberFormatException e) {
                        LOGGER.log(Level.WARNING,
                                "could not parse timeout value \"{0}\"",
                                timeoutString);
                    }
                }
            }
        }

        return -1;
    }

    private void updateSecondsLabel() {
        SpinnerNumberModel model
                = (SpinnerNumberModel) bootTimeoutSpinner.getModel();
        if (model.getNumber().intValue() == 1) {
            secondsLabel.setText(BUNDLE.getString("second"));
        } else {
            secondsLabel.setText(BUNDLE.getString("seconds"));
        }
    }

    /**
     * opens a clicked link in a browser
     *
     * @param evt the corresponding HyperlinkEvent
     */
    public static void openLinkInBrowser(HyperlinkEvent evt) {
        if (evt.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
//            try {
//                Desktop.getDesktop().browse(evt.getURL().toURI());
//            } catch (IOException ex) {
//                logger.log(Level.SEVERE, "could not open URL", ex);
//            } catch (URISyntaxException ex) {
//                logger.log(Level.SEVERE, "could not open URL", ex);
//            }

            // as long as Konqueror sucks so bad, we enforce firefox
            // (this is a quick and dirty solution, if konqueror starts to be
            // usable, switch back to the code above)
            final HyperlinkEvent finalEvent = evt;
            Thread browserThread = new Thread() {
                @Override
                public void run() {
                    PROCESS_EXECUTOR.executeProcess(new String[]{
                        "firefox", finalEvent.getURL().toString()});
                }
            };
            browserThread.start();
        }
    }

    private void selectCard(String cardName) {
        CardLayout cardLayout = (CardLayout) mainCardPanel.getLayout();
        cardLayout.show(mainCardPanel, cardName);
    }

    private void setProxyEnabled(boolean enabled) {
        proxyHostLabel.setEnabled(enabled);
        proxyHostTextField.setEnabled(enabled);
        proxyPortLabel.setEnabled(enabled);
        proxyPortTextField.setEnabled(enabled);
        proxyUserNameLabel.setEnabled(enabled);
        proxyUserNameTextField.setEnabled(enabled);
        proxyPasswordLabel.setEnabled(enabled);
        proxyPasswordField.setEnabled(enabled);
    }

    private String getWgetProxyLine() {
        if (proxyCheckBox.isSelected()) {
            String proxyHost = proxyHostTextField.getText();
            int proxyPort = ((Number) proxyPortTextField.getValue()).intValue();
            String proxyUserName = proxyUserNameTextField.getText();
            String proxyPassword
                    = String.valueOf(proxyPasswordField.getPassword());
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(" -e http_proxy=http://");
            stringBuilder.append(proxyHost);
            if (proxyPort != 0) {
                stringBuilder.append(':');
                stringBuilder.append(proxyPort);
            }
            if (!proxyUserName.isEmpty()) {
                stringBuilder.append(" --proxy-user=");
                stringBuilder.append(proxyUserName);
            }
            if (!proxyPassword.isEmpty()) {
                stringBuilder.append(" --proxy-password=");
                stringBuilder.append(proxyPassword);
            }
            stringBuilder.append(' ');
            return stringBuilder.toString();
        } else {
            return " ";
        }
    }

    private String getAptGetProxyLine() {
        if (proxyCheckBox.isSelected()) {
            return " -o " + getAptGetAcquireLine() + ' ';
        } else {
            return " ";
        }
    }

    private String getAptGetAcquireLine() {
        String proxyHost = proxyHostTextField.getText();
        int proxyPort = ((Number) proxyPortTextField.getValue()).intValue();
        String proxyUserName = proxyUserNameTextField.getText();
        String proxyPassword = String.valueOf(proxyPasswordField.getPassword());
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append("Acquire::http::proxy=http://");
        if (!proxyUserName.isEmpty()) {
            stringBuilder.append(proxyUserName);
            if (!proxyPassword.isEmpty()) {
                stringBuilder.append(':');
                stringBuilder.append(proxyPassword);
            }
            stringBuilder.append('@');
        }
        stringBuilder.append(proxyHost);
        if (proxyPort != 0) {
            stringBuilder.append(':');
            stringBuilder.append(proxyPort);
        }
        return stringBuilder.toString();
    }

    // XXX Still missing in Backend
    private void apply() throws DBusException {
        // make sure that all edits are applied to the IP table
        // and so some firewall sanity checks
        TableCellEditor editor = firewallIPTable.getCellEditor();
        if (editor != null) {
            editor.stopCellEditing();
        }
        if (examEnvironment) {
            if (!checkFirewall()) {
                return;
            }
            if (!checkBackupDirectory()) {
                return;
            }
        }

        // update full user name (if necessary)
        String newFullName = userNameTextField.getText();
        if (!newFullName.equals(fullName)) {
            LOGGER.log(Level.INFO,
                    "updating full user name to \"{0}\"", newFullName);
            PROCESS_EXECUTOR.executeProcess("chfn", "-f", newFullName, "user");
        }

        // update exchange partition label
        String newExchangePartitionLabel
                = exchangePartitionNameTextField.getText();
        LOGGER.log(Level.INFO, "new exchange partition label: \"{0}\"",
                newExchangePartitionLabel);
        if (!newExchangePartitionLabel.isEmpty()
                && !newExchangePartitionLabel.equals(exchangePartitionLabel)) {
            String binary = null;
            boolean umount = false;
            String idType = exchangePartition.getIdType();
            switch (idType) {
                case "vfat":
                    binary = "dosfslabel";
                    break;
                case "exfat":
                    binary = "exfatlabel";
                    break;
                case "ntfs":
                    binary = "ntfslabel";
                    // ntfslabel refuses to work on a mounted partition with the
                    // error message: "Cannot make changes to a mounted device".
                    // Therefore we have to try to umount the partition.
                    umount = true;
                    break;
                default:
                    LOGGER.log(Level.WARNING,
                            "no labeling binary for type \"{0}\"!", idType);
                    break;
            }
            if (binary != null) {
                boolean tmpUmount = umount && exchangePartition.isMounted();
                if (tmpUmount) {
                    exchangePartition.umount();
                }
                PROCESS_EXECUTOR.executeProcess(binary,
                        "/dev/" + exchangePartition.getDeviceAndNumber(),
                        newExchangePartitionLabel);
                if (tmpUmount) {
                    exchangePartition.mount();
                }
            }
        }

        String backupSource = backupSourceTextField.getText();
        String backupDirectory = backupDirectoryTextField.getText();
        String backupPartition = backupPartitionTextField.getText();

        if (examEnvironment) {

            updateFirewall();

            if (!backupDirectoryCheckBox.isSelected()
                    || backupDirectory.isEmpty()) {
                if (backupPartitionCheckBox.isSelected()
                        && !backupPartition.isEmpty()) {
                    updateJBackpackProperties(backupSource, "/mnt/backup/"
                            + backupPartition + "/lernstick_backup");
                }
            } else {
                updateJBackpackProperties(backupSource, backupDirectory);
            }

        } else {
            installSelectedPackages();
        }

        // update lernstickWelcome properties
        try {
            properties.setProperty(SHOW_WELCOME,
                    readWriteCheckBox.isSelected() ? "true" : "false");
            properties.setProperty(SHOW_READ_ONLY_INFO,
                    readOnlyCheckBox.isSelected() ? "true" : "false");
            properties.setProperty(BACKUP,
                    backupCheckBox.isSelected() ? "true" : "false");
            properties.setProperty(BACKUP_SCREENSHOT,
                    screenShotCheckBox.isSelected() ? "true" : "false");
            properties.setProperty(EXCHANGE_ACCESS,
                    exchangeAccessCheckBox.isSelected() ? "true" : "false");
            properties.setProperty(BACKUP_DIRECTORY_ENABLED,
                    backupDirectoryCheckBox.isSelected() ? "true" : "false");
            properties.setProperty(BACKUP_PARTITION_ENABLED,
                    backupPartitionCheckBox.isSelected() ? "true" : "false");
            properties.setProperty(BACKUP_SOURCE, backupSource);
            properties.setProperty(BACKUP_DIRECTORY, backupDirectory);
            properties.setProperty(BACKUP_PARTITION, backupPartition);
            Number backupFrequency = (Number) backupFrequencySpinner.getValue();
            properties.setProperty(BACKUP_FREQUENCY,
                    backupFrequency.toString());
            properties.setProperty(KDE_LOCK,
                    kdePlasmaLockCheckBox.isSelected() ? "true" : "false");
            properties.store(new FileOutputStream(propertiesFile),
                    "lernstick Welcome properties");
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        if (IMAGE_IS_WRITABLE) {
            updateBootloaders();
        }

        if (Files.exists(ALSA_PULSE_CONFIG_FILE)) {
            if (noPulseAudioCheckbox.isSelected()) {
                // divert alsa pulse config file
                PROCESS_EXECUTOR.executeProcess("dpkg-divert",
                        "--rename", ALSA_PULSE_CONFIG_FILE.toString());
            }
        } else if (!noPulseAudioCheckbox.isSelected()) {
            // restore original alsa pulse config file
            PROCESS_EXECUTOR.executeProcess("dpkg-divert", "--remove",
                    "--rename", ALSA_PULSE_CONFIG_FILE.toString());
        }

        // show "done" message
        // toolkit.beep();
        URL url = getClass().getResource(
                "/ch/fhnw/lernstickwelcome/KDE_Notify.wav");
        AudioClip clip = Applet.newAudioClip(url);
        clip.play();
        String infoMessage = BUNDLE.getString("Info_Success");
        JOptionPane.showMessageDialog(this, infoMessage,
                BUNDLE.getString("Information"),
                JOptionPane.INFORMATION_MESSAGE);
    }

    private void updateBootloaders() throws DBusException {
        // TODO Update Model
        SpinnerNumberModel spinnerNumberModel
                = (SpinnerNumberModel) bootTimeoutSpinner.getModel();
        final int timeout = spinnerNumberModel.getNumber().intValue();
        final String systemName = systemNameTextField.getText();
        final String systemVersion = systemVersionTextField.getText();

    }

    private void updateBootloaders(File directory, int timeout,
            String systemName, String systemVersion) throws DBusException {
            systemconfigTask.updateBootloader(directory, timeout, systemName, systemVersion);
    }

    private void updateFirewall() {
        firewallTask.updateFirewall();
    }

    private boolean checkBackupDirectory() {
        try {
            backupTask.checkBackupDirectory();
        } catch(ProcessingException ex) {
            showBackupDirectoryError(MessageFormat.format(BUNDLE.getString(ex.getMessage()), (Object[])ex.getMessageDetails()));
        }
    }

    private void showBackupDirectoryError(String errorMessage) {
        menuList.setSelectedValue(backupEntry, true);
        backupDirectoryTextField.requestFocusInWindow();
        showErrorMessage(errorMessage);
    }

    // XXX GUI
    private boolean checkFirewall() {
        try {
            for (int i = 0; i < ipTableModel.getRowCount(); i++) {
                WelcomeUtil.checkTarget((String) ipTableModel.getValueAt(i, 1), i);
                WelcomeUtil.checkPortRange((String) ipTableModel.getValueAt(i, 2), i);
            }
            return true;
        } catch(TableCellValidationException ex) {
            firewallError(MessageFormat.format(BUNDLE.getString(ex.getMessage()), ex.getMessageDetails()), ex.getRow(), ex.getCol());
            return false;
        }
    }

    public void portRangeError(int index) {
        String errorMessage = BUNDLE.getString("Error_PortRange");
        firewallError(errorMessage, index, 2);
    }

    // XXX GUI
    private void firewallError(String errorMessage, int row, int column) {
        menuList.setSelectedValue(firewallEntry, true);
        firewallTabbedPane.setSelectedIndex(0);
        firewallIPTable.clearSelection();
        firewallIPTable.addRowSelectionInterval(row, row);
        firewallIPTable.editCellAt(row, column);
        firewallIPTable.getEditorComponent().requestFocus();
        showErrorMessage(errorMessage);
    }

    private void updateJBackpackProperties(
            String backupSource, String backupDestination) {
        // update JBackpack preferences of the default user
        File prefsDirectory = new File(
                "/home/user/.java/.userPrefs/ch/fhnw/jbackpack/");
        updateJBackpackProperties(
                prefsDirectory, backupSource, backupDestination, true);

        // update JBackpack preferences of the root user
        prefsDirectory = new File(
                "/root/.java/.userPrefs/ch/fhnw/jbackpack/");
        updateJBackpackProperties(
                prefsDirectory, backupSource, backupDestination, false);
    }

    private void updateJBackpackProperties(File prefsDirectory,
            String backupSource, String backupDestination, boolean chown) {
        backupTask.updateJBackpackProperties(prefsDirectory, backupSource, backupDestination, chown);
    }

    private void updatePackagesLists() {
        // make sure that update-notifier does not get into our way
        String script = "#!/bin/sh\n"
                + "mykill() {\n"
                + "   ID=`ps -u 0 | grep \"${1}\" | awk '{ print $1 }'`\n"
                + "   if [ -n \"${ID}\" ]\n"
                + "   then\n"
                + "       kill -9 ${ID}\n"
                + "   fi\n"
                + "}\n"
                + "mykill /usr/lib/update-notifier/apt-check\n"
                + "mykill update-notifier";

        try {
            int exitValue = PROCESS_EXECUTOR.executeScript(script);
            if (exitValue != 0) {
                LOGGER.log(Level.WARNING, "Could not kill update-notifier: {0}",
                        PROCESS_EXECUTOR.getOutput());
            }
        } catch (IOException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }

        // update packaging information
        ProgressDialog updateDialog = new ProgressDialog(this,
                new ImageIcon(getClass().getResource(
                        "/ch/fhnw/lernstickwelcome/icons/download_anim.gif")));
        updateDialog.setProgressBarVisible(false);
        updateDialog.setTitle(null);
        PackageListUpdater packageListUpdater
                = new PackageListUpdater(updateDialog);
        packageListUpdater.execute();
        updateDialog.setVisible(true);
        try {
            if (!packageListUpdater.get()) {
                UpdateErrorDialog dialog
                        = new UpdateErrorDialog(this, aptGetOutput);
                dialog.setVisible(true);
            }
        } catch (InterruptedException | ExecutionException ex) {
            LOGGER.log(Level.SEVERE, null, ex);
        }
    }

    private void installSelectedPackages() {

        // calculate number of packages
        int numberOfPackages = 0;

        // non-free software
        numberOfPackages += flashCheckBox.isSelected() ? 1 : 0;
        numberOfPackages += readerCheckBox.isSelected() ? 1 : 0;
        numberOfPackages += additionalFontsCheckBox.isSelected() ? 1 : 0;
        numberOfPackages += multimediaCheckBox.isSelected() ? 1 : 0;
        numberOfPackages += googleEarthCheckBox.isSelected() ? 1 : 0;
        numberOfPackages += skypeCheckBox.isSelected() ? 1 : 0;
        numberOfPackages += virtualBoxCheckBox.isSelected() ? 1 : 0;

        // LA teaching tools
        numberOfPackages += laCheckBox.isSelected() ? 1 : 0;

        // miscellaneous
        numberOfPackages += gcomprisPanel.isSelected() ? 1 : 0;
        numberOfPackages += omnituxPanel.isSelected() ? 1 : 0;
        numberOfPackages += stellariumPanel.isSelected() ? 1 : 0;
        numberOfPackages += kstarsPanel.isSelected() ? 1 : 0;
        numberOfPackages += etoysPanel.isSelected() ? 1 : 0;
        numberOfPackages += wxMaximaPanel.isSelected() ? 1 : 0;
        numberOfPackages += tuxPaintPanel.isSelected() ? 1 : 0;
        numberOfPackages += netbeansPanel.isSelected() ? 1 : 0;
        numberOfPackages += processingPanel.isSelected() ? 1 : 0;
        numberOfPackages += rStudioPanel.isSelected() ? 1 : 0;
        numberOfPackages += lazarusPanel.isSelected() ? 1 : 0;
        numberOfPackages += openClipartPanel.isSelected() ? 1 : 0;
        numberOfPackages += sweetHome3DPanel.isSelected() ? 1 : 0;
        numberOfPackages += lyxPanel.isSelected() ? 1 : 0;
        numberOfPackages += scribusPanel.isSelected() ? 1 : 0;
        numberOfPackages += gespeakerPanel.isSelected() ? 1 : 0;
        numberOfPackages += gnucashPanel.isSelected() ? 1 : 0;
        numberOfPackages += rosegardenPanel.isSelected() ? 1 : 0;
        numberOfPackages += hydrogenPanel.isSelected() ? 1 : 0;
        numberOfPackages += webweaverdesktopPanel.isSelected() ? 1 : 0;
        numberOfPackages += wizbeePanel.isSelected() ? 1 : 0;
        numberOfPackages += calcularisPanel.isSelected() ? 1 : 0;
        numberOfPackages += lehrerOfficePanel.isSelected() ? 1 : 0;

        // games
        numberOfPackages += colobotGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += riliGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += filletsGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += neverballGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += neverputtGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += freecolGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += minetestGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += frogattoGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += supertuxGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += supertuxkartGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += xmotoGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += triggerGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += openClonkPanel.isSelected() ? 1 : 0;
        numberOfPackages += wesnothGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += flareGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += hedgewarsGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += megaglestGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += ufoaiGamePanel.isSelected() ? 1 : 0;
        numberOfPackages += astromenaceGamePanel.isSelected() ? 1 : 0;

        LOGGER.log(Level.INFO, "number of packages = {0}", numberOfPackages);

        if (numberOfPackages > 0) {

            updatePackagesLists();

            final ProgressDialog progressDialog = new ProgressDialog(this,
                    new ImageIcon(getClass().getResource(
                            "/ch/fhnw/lernstickwelcome/icons/download_anim.gif")));

            Installer installer
                    = new Installer(progressDialog, numberOfPackages);
            installer.addPropertyChangeListener(
                    new PropertyChangeListener() {
                @Override
                public void propertyChange(PropertyChangeEvent evt) {
                    if ("progress".equals(evt.getPropertyName())) {
                        Integer progress = (Integer) evt.getNewValue();
                        progressDialog.setProgress(progress);
                    }
                }
            });
            installer.execute();
            progressDialog.setVisible(true);

            checkAllPackages();
        }
    }

    private void checkAllPackages() {
        // check which applications are already installed

        // nonfree software
        checkInstall(flashCheckBox, flashLabel,
                "Welcome.flashLabel.text", FLASH_PACKAGES);
        checkInstall(readerCheckBox, readerLabel,
                "Welcome.readerLabel.text", "adobereader-enu");
        checkInstall(additionalFontsCheckBox, fontsLabel,
                "Welcome.fontsLabel.text", FONTS_PACKAGES);
        checkInstall(multimediaCheckBox, multimediaLabel,
                "Welcome.multimediaLabel.text", MULTIMEDIA_PACKAGES);
        checkInstall(googleEarthCheckBox, googleEarthLabel,
                "Welcome.googleEarthLabel.text", "google-earth-stable");
        checkInstall(skypeCheckBox, skypeLabel,
                "Welcome.skypeLabel.text", "skype");
        checkInstall(virtualBoxCheckBox, virtualBoxLabel,
                "Welcome.virtualBoxLabel.text", "virtualbox-ext-pack");

        // LA Teaching System
        checkInstall(laCheckBox, laLabel,
                "Welcome.laLabel.text", "lateaching");

        // miscellaneous
        checkAppInstall(gcomprisPanel, "gcompris");
        checkAppInstall(omnituxPanel, "omnitux");
        checkAppInstall(stellariumPanel, "lernstick-stellarium");
        checkAppInstall(kstarsPanel, "lernstick-kstars");
        checkAppInstall(etoysPanel, "lernstick-etoys");
        checkAppInstall(wxMaximaPanel, "lernstick-wxmaxima");
        checkAppInstall(tuxPaintPanel, "lernstick-tuxpaint");
        checkAppInstall(netbeansPanel, "lernstick-netbeans");
        checkAppInstall(processingPanel, "processing");
        checkAppInstall(rStudioPanel, "rstudio");
        checkAppInstall(lazarusPanel, "lazarus");
        checkAppInstall(openClipartPanel, "openclipart-libreoffice");
        checkAppInstall(sweetHome3DPanel, "sweethome3d");
        checkAppInstall(lyxPanel, "lyx");
        checkAppInstall(scribusPanel, "scribus");
        checkAppInstall(gespeakerPanel, "gespeaker");
        checkAppInstall(gnucashPanel, "gnucash");
        checkAppInstall(rosegardenPanel, "rosegarden");
        checkAppInstall(hydrogenPanel, "hydrogen");
        checkAppInstall(webweaverdesktopPanel, "webweaverdesktop");
        checkAppInstall(wizbeePanel, "wizbee");
        checkAppInstall(calcularisPanel, "calcularis-de");
        checkAppInstall(lehrerOfficePanel, "lehreroffice");

        // games
        checkAppInstall(colobotGamePanel, "colobot");
        checkAppInstall(riliGamePanel, "ri-li");
        checkAppInstall(filletsGamePanel, "lernstick-fillets-ng");
        checkAppInstall(neverballGamePanel, "live-neverball");
        checkAppInstall(neverputtGamePanel, "live-neverputt");
        checkAppInstall(frogattoGamePanel, "lernstick-frogatto");
        checkAppInstall(freecolGamePanel, "freecol");
        checkAppInstall(minetestGamePanel, "minetest");
        checkAppInstall(supertuxGamePanel, "live-supertux");
        checkAppInstall(supertuxkartGamePanel, "supertuxkart");
        checkAppInstall(xmotoGamePanel, "xmoto");
        checkAppInstall(triggerGamePanel, "live-trigger-rally");
        checkAppInstall(openClonkPanel, "openclonk");
        checkAppInstall(wesnothGamePanel, "wesnoth-1.12");
        checkAppInstall(flareGamePanel, "flare-game");
        checkAppInstall(hedgewarsGamePanel, "hedgewars");
        checkAppInstall(megaglestGamePanel, "megaglest");
        checkAppInstall(ufoaiGamePanel, "ufoai");
        checkAppInstall(astromenaceGamePanel, "lernstick-astromenace");
    }

    private void checkInstall(JCheckBox checkBox, JLabel label,
            String guiKey, String... packages) {
        if (arePackagesInstalled(packages)) {
            checkBox.setSelected(false);
            String newString = BUNDLE.getString("Already_Installed_Template");
            newString = MessageFormat.format(
                    newString, BUNDLE.getString(guiKey));
            label.setText(newString);
        }
    }

    private void checkAppInstall(GamePanel gamePanel, String... packages) {
        gamePanel.setInstalled(arePackagesInstalled(packages));
    }

    private boolean arePackagesInstalled(String... packages) {
        int length = packages.length;
        String[] commandArray = new String[length + 2];
        commandArray[0] = "dpkg";
        commandArray[1] = "-l";
        System.arraycopy(packages, 0, commandArray, 2, length);
        PROCESS_EXECUTOR.executeProcess(true, true, commandArray);
        List<String> stdOut = PROCESS_EXECUTOR.getStdOutList();
        for (String packageName : packages) {
            LOGGER.log(Level.INFO, "checking package {0}", packageName);
            Pattern pattern = Pattern.compile("^ii  " + packageName + ".*");
            boolean found = false;
            for (String line : stdOut) {
                if (pattern.matcher(line).matches()) {
                    LOGGER.info("match");
                    found = true;
                    break;
                } else {
                    LOGGER.info("no match");
                }
            }
            if (!found) {
                LOGGER.log(Level.INFO,
                        "package {0} not installed", packageName);
                return false;
            }
        }
        return true;
    }

    private void toggleCheckBox(JCheckBox checkBox) {
        if (checkBox.isEnabled()) {
            checkBox.setSelected(!checkBox.isSelected());
        }
    }

    private void showErrorMessage(String errorMessage) {
        JOptionPane.showMessageDialog(this, errorMessage,
                BUNDLE.getString("Error"), JOptionPane.ERROR_MESSAGE);

    }

    private class PackageListUpdater
            extends SwingWorker<Boolean, ProgressAction> {

        private final ProgressDialog progressDialog;

        public PackageListUpdater(ProgressDialog progressDialog) {
            this.progressDialog = progressDialog;
        }

        @Override
        protected Boolean doInBackground() throws Exception {
            String infoString = BUNDLE.getString("Updating_Packagelist");
            Icon icon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/lernstickwelcome/icons/package.png"));
            publish(new ProgressAction(infoString, icon));

            String updateScript = "cd " + USER_HOME + '\n'
                    + "apt-get" + getAptGetProxyLine() + "update";
            int exitValue = PROCESS_EXECUTOR.executeScript(
                    true, true, updateScript);
            if (exitValue != 0) {
                aptGetOutput = PROCESS_EXECUTOR.getOutput();
                String logMessage = "apt-get failed with the following "
                        + "output:\n" + aptGetOutput;
                LOGGER.severe(logMessage);
                return false;
            }
            return true;
        }

        @Override
        protected void process(List<ProgressAction> appInfos) {
            progressDialog.setProgressAction(appInfos.get(0));
        }

        @Override
        protected void done() {
            progressDialog.setVisible(false);
        }
    }

    private class Installer extends SwingWorker<Boolean, ProgressAction> {

        private final ProgressDialog progressDialog;
        private final int numberOfPackages;
        private int currentPackage;

        public Installer(
                ProgressDialog progressDialog, int numberOfPackages) {
            this.progressDialog = progressDialog;
            this.numberOfPackages = numberOfPackages;
        }

        @Override
        protected Boolean doInBackground() throws Exception {

            // nonfree packages
            installNonFreeApplication(flashCheckBox, "Welcome.flashLabel.text",
                    "/ch/fhnw/lernstickwelcome/icons/48x48/Adobe_Flash_cs3.png",
                    FLASH_PACKAGES);
            installAdobeReader();
            installNonFreeApplication(additionalFontsCheckBox,
                    "Welcome.fontsLabel.text",
                    "/ch/fhnw/lernstickwelcome/icons/48x48/fonts.png",
                    FONTS_PACKAGES);
            installNonFreeApplication(multimediaCheckBox,
                    "Welcome.multimediaLabel.text",
                    "/ch/fhnw/lernstickwelcome/icons/48x48/package_multimedia.png",
                    MULTIMEDIA_PACKAGES);
            installGoogleEarth();
            installSkype();
            installNonFreeApplication(virtualBoxCheckBox,
                    "Welcome.virtualBoxLabel.text",
                    "/ch/fhnw/lernstickwelcome/icons/48x48/virtualbox.png",
                    "virtualbox-ext-pack");

            // teaching system
            installNonFreeApplication(laCheckBox, "LA_Teaching_System",
                    "/ch/fhnw/lernstickwelcome/icons/48x48/LinuxAdvanced.png",
                    "lateaching");

            // miscellaneous
            installApplication(gcomprisPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/gcompris.png",
                    "lernstick-gcompris", "gcompris", "gcompris-sound-de",
                    "gcompris-sound-en", "gcompris-sound-es",
                    "gcompris-sound-fr", "gcompris-sound-it",
                    "gcompris-sound-ptbr", "gcompris-sound-ru");

            installApplication(omnituxPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/omnitux.png",
                    "omnitux");
            installApplication(stellariumPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/stellarium.png",
                    "lernstick-stellarium");
            installApplication(kstarsPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/kstars.png",
                    "lernstick-kstars", "kstars",
                    "kstars-data", "kstars-data-extra-tycho2");
            installApplication(etoysPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/etoys.png",
                    "lernstick-etoys", "lernstick-squeak-vm");
            installApplication(wxMaximaPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/wxmaxima.png",
                    "lernstick-wxmaxima");
            installApplication(tuxPaintPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/tuxpaint.png",
                    "lernstick-tuxpaint");
            installApplication(netbeansPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/netbeans.png",
                    "lernstick-netbeans", "lernstick-visualvm",
                    "lernstick-scenebuilder", "scenebuilder",
                    "openjdk-8-source", "openjdk-8-doc",
                    "openjfx", "openjfx-source", "libopenjfx-java-doc");
            installApplication(processingPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/processing.png",
                    "processing");
            installApplication(rStudioPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/rstudio.png",
                    "rstudio", "r-base", "lernstick-r-base-core");
            installApplication(lazarusPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/lazarus.png",
                    "lazarus", "fpc-source", "lcl",
                    "fp-units-gfx", "fp-units-gtk", "fp-units-misc");
            installApplication(openClipartPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/openclipart.png",
                    "openclipart-libreoffice");
            installApplication(sweetHome3DPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/sweethome3d.png",
                    "sweethome3d", "sweethome3d-furniture",
                    "sweethome3d-furniture-nonfree");
            installApplication(gnucashPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/gnucash.png",
                    "gnucash", "gnucash-docs");
            installApplication(lyxPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/lyx.png",
                    "lyx", "fonts-lyx", "lernstick-lyx", "texlive-lang-all",
                    "texlive-latex-extra", "texlive-fonts-recommended",
                    "texlive-fonts-extra", "texlive-font-utils",
                    "texlive-generic-extra", "texlive-generic-recommended",
                    "texlive-science", "texlive-humanities", "tipa");
            installApplication(scribusPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/scribus.png",
                    "scribus", "scribus-template", "scribus-doc",
                    "lernstick-scribus");
            installApplication(gespeakerPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/gespeaker.png",
                    "gespeaker",
                    "mbrola-br1", "mbrola-br3", "mbrola-br4", "mbrola-de2",
                    "mbrola-de3", "mbrola-de4", "mbrola-de5", "mbrola-de6",
                    "mbrola-de7", "mbrola-en1", "mbrola-es1", "mbrola-es2",
                    "mbrola-fr1", "mbrola-fr4", "mbrola-it3", "mbrola-it4",
                    "mbrola-us1", "mbrola-us2", "mbrola-us3");
            installApplication(rosegardenPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/rosegarden.png",
                    "rosegarden", "fluid-soundfont-gm",
                    "fluid-soundfont-gs", "fluidsynth-dssi");
            installApplication(hydrogenPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/hydrogen.png",
                    "hydrogen",
                    "hydrogen-drumkits", "hydrogen-drumkits-effects");
            installApplication(webweaverdesktopPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/webweaverdesktop.png",
                    "webweaverdesktop");
            installApplication(wizbeePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/wizbee.png",
                    "wizbee");
            installApplication(calcularisPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/calcularis.png",
                    "calcularis-de");
            installApplication(lehrerOfficePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/lehreroffice.png",
                    "lehreroffice");

            // games
            installApplication(colobotGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/colobot.png",
                    "colobot");
            installApplication(riliGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/ri-li.png",
                    "ri-li");
            installApplication(filletsGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/fillets.png",
                    "lernstick-fillets-ng", "fillets-ng-data-cs");
            installApplication(neverballGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/neverball.png",
                    "live-neverball");
            installApplication(neverputtGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/neverputt.png",
                    "live-neverputt");
            installApplication(freecolGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/freecol.png",
                    "freecol");
            installApplication(minetestGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/minetest.png",
                    "lernstick-minetest-game-bfd",
                    "lernstick-minetest-game-carbone-ng",
                    "lernstick-minetest-game-tutorial",
                    "lernstick-minetest-mod-dreambuilder");
            installApplication(frogattoGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/frogatto.png",
                    "lernstick-frogatto");
            installApplication(supertuxGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/supertux.png",
                    "live-supertux");
            installApplication(supertuxkartGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/supertuxkart.png",
                    "live-supertuxkart");
            installApplication(xmotoGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/xmoto.png",
                    "live-xmoto");
            installApplication(triggerGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/trigger.png",
                    "live-trigger-rally");
            installApplication(openClonkPanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/openclonk.png",
                    "openclonk");
            installApplication(wesnothGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/wesnoth.png",
                    "wesnoth-1.12", "wesnoth-1.12-music");
            installApplication(flareGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/flare.png",
                    "flare-game");
            installApplication(hedgewarsGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/hedgewars.png",
                    "hedgewars");
            installApplication(megaglestGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/megaglest.png",
                    "megaglest");
            installApplication(ufoaiGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/ufoai.png",
                    "ufoai", "ufoai-music");
            installApplication(astromenaceGamePanel,
                    "/ch/fhnw/lernstickwelcome/icons/48x48/astromenace.png",
                    "lernstick-astromenace");

            return null;
        }

        @Override
        protected void process(List<ProgressAction> appInfos) {
            progressDialog.setProgressAction(appInfos.get(0));
        }

        @Override
        protected void done() {
            progressDialog.setVisible(false);
        }

        private void installAdobeReader() throws IOException {
            if (!readerCheckBox.isSelected()) {
                return;
            }
            String infoString = BUNDLE.getString("Installing");
            infoString = MessageFormat.format(infoString,
                    BUNDLE.getString("Welcome.readerLabel.text"));
            Icon icon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/lernstickwelcome/icons/48x48/Adobe_Reader_8_icon.png"));
            ProgressAction progressAction
                    = new ProgressAction(infoString, icon);
            publish(progressAction);
            String fileName = "AdbeRdr9.5.5-1_i386linux_enu.deb";
            String adobeReaderInstallScript = "cd " + USER_HOME + '\n'
                    + "wget" + getWgetProxyLine()
                    + "ftp://ftp.adobe.com/pub/adobe/reader/unix/9.x/9.5.5/enu/"
                    + fileName + '\n'
                    + "dpkg -i " + fileName + '\n'
                    + "rm " + fileName;
            int exitValue = PROCESS_EXECUTOR.executeScript(
                    true, true, adobeReaderInstallScript);
            if (exitValue == 0) {
                aptGetInstall("lernstick-adobereader-enu");
            } else {
                String errorMessage = "Installation of Adobe Reader failed"
                        + "with the following error message:\n"
                        + PROCESS_EXECUTOR.getOutput();
                LOGGER.severe(errorMessage);
                showErrorMessage(errorMessage);
            }
            updateProgress();
        }

        private void installSkype() throws IOException {
            if (!skypeCheckBox.isSelected()) {
                return;
            }
            String infoString = BUNDLE.getString("Installing");
            infoString = MessageFormat.format(infoString,
                    BUNDLE.getString("Welcome.skypeLabel.text"));
            Icon icon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/lernstickwelcome/icons/48x48/skype.png"));
            ProgressAction progressAction
                    = new ProgressAction(infoString, icon);
            publish(progressAction);
            String skypeInstallScript = "#!/bin/sh\n"
                    + "wget -O skype-install.deb http://www.skype.com/go/getskype-linux-deb\n"
                    + "dpkg -i skype-install.deb\n"
                    + "apt-get -f install\n"
                    + "rm skype-install.deb";
            int exitValue = PROCESS_EXECUTOR.executeScript(
                    true, true, skypeInstallScript);
            if (exitValue != 0) {
                String errorMessage = "Installation of Skype failed"
                        + "with the following error message:\n"
                        + PROCESS_EXECUTOR.getOutput();
                LOGGER.severe(errorMessage);
                showErrorMessage(errorMessage);
            }
            updateProgress();
        }

        private void installGoogleEarth() throws IOException {
            if (!googleEarthCheckBox.isSelected()) {
                return;
            }
            String infoString = BUNDLE.getString("Installing");
            infoString = MessageFormat.format(infoString,
                    BUNDLE.getString("Welcome.googleEarthLabel.text"));
            Icon icon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/lernstickwelcome/icons/48x48/googleearth-icon.png"));
            ProgressAction progressAction
                    = new ProgressAction(infoString, icon);
            publish(progressAction);

            // old version with googleearth-package
//            String googleEarthInstallScript = "cd " + USER_HOME + '\n'
//                    + "apt-get -y --force-yes install googleearth-package lsb-core\n"
//                    + "make-googleearth-package --force\n"
//                    + "dpkg -i googleearth_*\n"
//                    + "rm googleearth_*";
            // new version with direct download link
            String debName = "google-earth-stable_current_i386.deb";
            String googleEarthInstallScript = "apt-get" + getAptGetProxyLine()
                    + "-y --force-yes install lsb-core\n"
                    + "cd " + USER_HOME + '\n'
                    + "wget" + getWgetProxyLine()
                    + "http://dl.google.com/dl/earth/client/current/" + debName + '\n'
                    + "dpkg -i " + debName + '\n'
                    + "rm " + debName;
            int exitValue = PROCESS_EXECUTOR.executeScript(
                    true, true, googleEarthInstallScript);
            if (exitValue != 0) {
                String errorMessage = "Installation of GoogleEarth failed"
                        + "with the following error message:\n"
                        + PROCESS_EXECUTOR.getOutput();
                LOGGER.severe(errorMessage);
                showErrorMessage(errorMessage);
            }
            updateProgress();
        }

        private void installGoogleChrome() throws IOException {
            String infoString = BUNDLE.getString("Installing");
            infoString = MessageFormat.format(infoString,
                    BUNDLE.getString("Welcome.googleChromeLabel.text"));
            Icon icon = new ImageIcon(getClass().getResource(
                    "/ch/fhnw/lernstickwelcome/icons/48x48/chrome.png"));
            ProgressAction progressAction
                    = new ProgressAction(infoString, icon);
            publish(progressAction);

            String debName = "google-chrome-stable_current_i386.deb";
            String googleEarthInstallScript = "wget" + getWgetProxyLine()
                    + "https://dl.google.com/linux/direct/" + debName + '\n'
                    + "dpkg -i " + debName + '\n'
                    + "rm " + debName;
            int exitValue = PROCESS_EXECUTOR.executeScript(
                    true, true, googleEarthInstallScript);
            if (exitValue != 0) {
                String errorMessage = "Installation of Google Chrome failed"
                        + "with the following error message:\n"
                        + PROCESS_EXECUTOR.getOutput();
                LOGGER.severe(errorMessage);
                showErrorMessage(errorMessage);
            }
            updateProgress();
        }

        private void installNonFreeApplication(JCheckBox checkBox, String key,
                String iconPath, String... packageNames) {
            if (!checkBox.isSelected()) {
                LOGGER.log(Level.INFO, "checkBox not selected: {0}", checkBox);
                return;
            }
            String infoString = MessageFormat.format(
                    BUNDLE.getString("Installing"), BUNDLE.getString(key));
            installPackage(infoString, iconPath, packageNames);
        }

        private void installApplication(GamePanel gamePanel, String iconPath,
                String... packageNames) {
            if (!gamePanel.isSelected()) {
                LOGGER.log(Level.INFO,
                        "gamePanel not selected: {0}", gamePanel.getGameName());
                return;
            }
            String infoString = MessageFormat.format(
                    BUNDLE.getString("Installing"), gamePanel.getGameName());
            installPackage(infoString, iconPath, packageNames);
        }

        private void installPackage(String infoString,
                String iconPath, String... packageNames) {
            Icon icon = new ImageIcon(getClass().getResource(iconPath));
            ProgressAction progressAction
                    = new ProgressAction(infoString, icon);
            publish(progressAction);
            aptGetInstall(packageNames);
            updateProgress();
        }

        private void aptGetInstall(String... packageNames) {
            for (String packageName : packageNames) {
                LOGGER.log(Level.INFO,
                        "installing package \"{0}\"", packageName);
            }
            StringBuilder builder = new StringBuilder();
            builder.append("#!/bin/sh\n"
                    + "export DEBIAN_FRONTEND=noninteractive\n");
            builder.append("apt-get ");
            if (proxyCheckBox.isSelected()) {
                builder.append("-o ");
                builder.append(getAptGetAcquireLine());
                builder.append(' ');
            }
            builder.append("-y --force-yes install ");
            for (String packageName : packageNames) {
                builder.append(packageName);
                builder.append(' ');
            }
            String script = builder.toString();

//            // enforce non-interactive installs
//            Map<String,String> environment = new HashMap<String, String>();
//            environment.put("DEBIAN_FRONTEND", "noninteractive");
//            processExecutor.setEnvironment(environment);
            int exitValue = -1;
            try {
                exitValue = PROCESS_EXECUTOR.executeScript(true, true, script);
            } catch (IOException ex) {
                LOGGER.log(Level.SEVERE, "", ex);
            } finally {
                if (exitValue != 0) {
                    String errorMessage = "apt-get failed with the following "
                            + "output:\n" + PROCESS_EXECUTOR.getOutput();
                    LOGGER.severe(errorMessage);
                    showErrorMessage(errorMessage);
                }
            }
        }

        private void updateProgress() {
            currentPackage++;
            setProgress((100 * currentPackage) / numberOfPackages);
        }
    }

    private class MyListCellRenderer extends DefaultListCellRenderer {

        @Override
        public Component getListCellRendererComponent(JList list, Object value,
                int index, boolean isSelected, boolean cellHasFocus) {
            MainMenuListEntry entry = (MainMenuListEntry) value;
            JLabel label = (JLabel) super.getListCellRendererComponent(
                    list, entry.getText(), index, isSelected, cellHasFocus);
            label.setIcon(entry.getIcon());
            label.setBorder(new EmptyBorder(5, 5, 5, 5));
            return label;
        }
    }
    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton addIPButton;
    private javax.swing.JCheckBox additionalFontsCheckBox;
    private javax.swing.JLabel additionalInfoLabel;
    private javax.swing.JPanel additionalPanel;
    private javax.swing.JScrollPane additionalScrollPane;
    private javax.swing.JPanel additionalScrollPanel;
    private javax.swing.JTabbedPane additionalTabbedPane;
    private javax.swing.JCheckBox allowFilesystemMountCheckbox;
    private javax.swing.JButton applyButton;
    private javax.swing.JCheckBox backupCheckBox;
    private javax.swing.JPanel backupDestinationsPanel;
    private javax.swing.JButton backupDirectoryButton;
    private javax.swing.JCheckBox backupDirectoryCheckBox;
    private javax.swing.JLabel backupDirectoryLabel;
    private javax.swing.JTextField backupDirectoryTextField;
    private javax.swing.JLabel backupFrequencyEveryLabel;
    private javax.swing.JLabel backupFrequencyMinuteLabel;
    private javax.swing.JSpinner backupFrequencySpinner;
    private javax.swing.JPanel backupPanel;
    private javax.swing.JCheckBox backupPartitionCheckBox;
    private javax.swing.JLabel backupPartitionLabel;
    private javax.swing.JTextField backupPartitionTextField;
    private javax.swing.JButton backupSourceButton;
    private javax.swing.JLabel backupSourceLabel;
    private javax.swing.JPanel backupSourcePanel;
    private javax.swing.JTextField backupSourceTextField;
    private javax.swing.JPanel bootMenuPanel;
    private javax.swing.JLabel bootTimeoutLabel;
    private javax.swing.JSpinner bootTimeoutSpinner;
    private javax.swing.JPanel bottomPanel;
    private javax.swing.JButton cancelButton;
    private javax.swing.JPanel dataPartitionPanel;
    private javax.swing.JCheckBox exchangeAccessCheckBox;
    private javax.swing.JLabel exchangePartitionNameLabel;
    private javax.swing.JTextField exchangePartitionNameTextField;
    private javax.swing.JPanel exchangePartitionPanel;
    private javax.swing.JLabel exchangeRebootLabel;
    private javax.swing.JPanel fillPanel;
    private javax.swing.JPanel firewallIPButtonPanel;
    private javax.swing.JScrollPane firewallIPScrollPane;
    private javax.swing.JTable firewallIPTable;
    private javax.swing.JLabel firewallInfoLabel;
    private javax.swing.JPanel firewallPanel;
    private javax.swing.JButton firewallStartStopButton;
    private javax.swing.JLabel firewallStatusLabel;
    private javax.swing.JTabbedPane firewallTabbedPane;
    private javax.swing.JPanel firewallURLPanel;
    private javax.swing.JScrollPane firewallURLScrollPane;
    private javax.swing.JTextArea firewallURLTextArea;
    private javax.swing.JPanel firewallipv4Panel;
    private javax.swing.JCheckBox flashCheckBox;
    private javax.swing.JLabel flashLabel;
    private javax.swing.JLabel fontsLabel;
    private javax.swing.JScrollPane gamesScrollPane;
    private javax.swing.JPanel gamesScrollPanel;
    private javax.swing.JCheckBox googleEarthCheckBox;
    private javax.swing.JLabel googleEarthLabel;
    private javax.swing.JScrollPane helpScrollPane;
    private javax.swing.JTextPane helpTextPane;
    private javax.swing.JEditorPane infoEditorPane;
    private javax.swing.JPanel infoPanel;
    private javax.swing.JScrollPane infoScrollPane;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JCheckBox kdePlasmaLockCheckBox;
    private javax.swing.JCheckBox laCheckBox;
    private javax.swing.JLabel laLabel;
    private javax.swing.JLabel label1;
    private javax.swing.JLabel label2;
    private javax.swing.JPanel mainCardPanel;
    private javax.swing.JList menuList;
    private javax.swing.JScrollPane menuScrollPane;
    private javax.swing.JPanel miscPanel;
    private javax.swing.JButton moveDownIPButton;
    private javax.swing.JButton moveUpIPButton;
    private javax.swing.JCheckBox multimediaCheckBox;
    private javax.swing.JLabel multimediaLabel;
    private javax.swing.JPanel navigaionPanel;
    private javax.swing.JButton nextButton;
    private javax.swing.JCheckBox noPulseAudioCheckbox;
    private javax.swing.JLabel nonfreeLabel;
    private javax.swing.JPanel nonfreePanel;
    private javax.swing.JPanel partitionsPanel;
    private javax.swing.JButton passwordChangeButton;
    private javax.swing.JLabel passwordChangeInfoLabel;
    private javax.swing.JPanel passwordChangePanel;
    private javax.swing.JPasswordField passwordField1;
    private javax.swing.JPasswordField passwordField2;
    private javax.swing.JButton previousButton;
    private javax.swing.JCheckBox proxyCheckBox;
    private javax.swing.JLabel proxyHostLabel;
    private javax.swing.JTextField proxyHostTextField;
    private javax.swing.JLabel proxyInfoLabel;
    private javax.swing.JPanel proxyPanel;
    private javax.swing.JPasswordField proxyPasswordField;
    private javax.swing.JLabel proxyPasswordLabel;
    private javax.swing.JLabel proxyPortLabel;
    private javax.swing.JFormattedTextField proxyPortTextField;
    private javax.swing.JLabel proxyUserNameLabel;
    private javax.swing.JTextField proxyUserNameTextField;
    private javax.swing.JCheckBox readOnlyCheckBox;
    private javax.swing.JPanel readOnlyPanel;
    private javax.swing.JCheckBox readWriteCheckBox;
    private javax.swing.JPanel readWritePanel;
    private javax.swing.JCheckBox readerCheckBox;
    private javax.swing.JLabel readerLabel;
    private javax.swing.JPanel recommendedPanel;
    private javax.swing.JButton removeIPButton;
    private javax.swing.JCheckBox screenShotCheckBox;
    private javax.swing.JLabel secondsLabel;
    private javax.swing.JCheckBox skypeCheckBox;
    private javax.swing.JLabel skypeLabel;
    private javax.swing.JLabel systemNameLabel;
    private javax.swing.JTextField systemNameTextField;
    private javax.swing.JPanel systemPanel;
    private javax.swing.JLabel systemVersionLabel;
    private javax.swing.JTextField systemVersionTextField;
    private javax.swing.JEditorPane teachingEditorPane;
    private javax.swing.JPanel teachingPanel;
    private javax.swing.JScrollPane teachingScrollPane;
    private javax.swing.JLabel userNameLabel;
    private javax.swing.JTextField userNameTextField;
    private javax.swing.JCheckBox virtualBoxCheckBox;
    private javax.swing.JLabel virtualBoxLabel;
    private javax.swing.JLabel welcomeLabel;
    // End of variables declaration//GEN-END:variables
}
