/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.fhnw.lernstickwelcome.controller;

import ch.fhnw.lernstickwelcome.model.PropertiesTask;
import ch.fhnw.lernstickwelcome.model.TaskProcessor;
import ch.fhnw.lernstickwelcome.model.WelcomeModelFactory;
import ch.fhnw.lernstickwelcome.model.application.ApplicationGroupTask;
import ch.fhnw.lernstickwelcome.model.backup.BackupTask;
import ch.fhnw.lernstickwelcome.model.firewall.FirewallTask;
import ch.fhnw.lernstickwelcome.model.partition.PartitionTask;
import ch.fhnw.lernstickwelcome.model.proxy.ProxyTask;
import ch.fhnw.lernstickwelcome.model.systemconfig.SystemconfigTask;
import java.util.ArrayList;
import java.util.List;
import java.util.ResourceBundle;
import javafx.concurrent.Task;

/**
 *
 * @author sschw
 */
public class WelcomeController {
    private ResourceBundle rb;
    
    private TaskProcessor installer;
    // Backend Tasks
    private PropertiesTask properties;
    // Standard Environment
    private ProxyTask proxy;
    private ApplicationGroupTask recApps;
    private ApplicationGroupTask teachApps;
    private ApplicationGroupTask softwApps;
    private ApplicationGroupTask gamesApps;
    private PartitionTask partition;
    // Exam Environment
    private FirewallTask firewall;
    private BackupTask backup;
    // General
    private SystemconfigTask sysconf;
    
    private boolean  isExamEnvironment;
    
    public void loadExamEnvironment(ResourceBundle rb) {
        this.rb = rb;

        properties = WelcomeModelFactory.getPropertiesTask();
        firewall = WelcomeModelFactory.getFirewallTask();
        backup = WelcomeModelFactory.getBackupTask(properties, rb.getString("Backup_Directory"));
        sysconf = WelcomeModelFactory.getSystemTask(true, properties);
        partition = WelcomeModelFactory.getPartitionTask(properties);
        
        List<Task> processingList = new ArrayList<Task>();
        processingList.add(firewall);
        processingList.add(backup);
        processingList.add(sysconf);
        processingList.add(partition);
        processingList.add(properties);
        installer = new TaskProcessor(processingList);
    }
    
    public void loadStandardEnvironment(ResourceBundle rb) {
        this.rb = rb;
        
        isExamEnvironment = false;
        // Init Model
        properties = WelcomeModelFactory.getPropertiesTask();
        proxy = WelcomeModelFactory.getProxyTask();
        recApps = WelcomeModelFactory.getRecommendedApplicationTask(proxy);
        
        sysconf = WelcomeModelFactory.getSystemTask(false, properties);
        
        // Init Installer
        List<Task> processingList = new ArrayList<Task>();
        processingList.add(proxy);
        processingList.add(recApps);
        
        installer = new TaskProcessor(processingList);
    }
    
    public void startInstallation() {
        installer.install();
    }
    
    public void closeApplication() {
        if(isExamEnvironment) {
            firewall.stopFirewallStateChecking();
        }
        sysconf.umountBootConfig();
    }

    public TaskProcessor getInstaller() {
        return installer;
    }

    public ProxyTask getProxy() {
        return proxy;
    }

    public ApplicationGroupTask getRecApps() {
        return recApps;
    }

    public ApplicationGroupTask getTeachApps() {
        return teachApps;
    }

    public ApplicationGroupTask getSoftwApps() {
        return softwApps;
    }

    public ApplicationGroupTask getGamesApps() {
        return gamesApps;
    }

    public PartitionTask getPartition() {
        return partition;
    }

    public FirewallTask getFirewall() {
        return firewall;
    }

    public BackupTask getBackup() {
        return backup;
    }

    public SystemconfigTask getSysconf() {
        return sysconf;
    }
    
}
