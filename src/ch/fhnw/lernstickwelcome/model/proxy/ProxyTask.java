/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package ch.fhnw.lernstickwelcome.model.proxy;

import ch.fhnw.lernstickwelcome.model.Processable;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.concurrent.Task;

/**
 *
 * @author sschw
 */
public class ProxyTask implements Processable<Boolean> {

    private BooleanProperty proxyActive = new SimpleBooleanProperty();
    private StringProperty hostname = new SimpleStringProperty();
    private StringProperty port = new SimpleStringProperty();
    private StringProperty username = new SimpleStringProperty();
    private StringProperty password = new SimpleStringProperty();

    // Init to prevent typos in commands if inactive
    private String wgetProxy = " ";
    private String aptGetProxy = " ";

    public ProxyTask() {
    }

    public String getWgetProxy() {
        return wgetProxy;
    }

    public String getAptGetProxy() {
        return aptGetProxy;
    }

    private void setupWgetProxy() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" -e http_proxy=http://");
        stringBuilder.append(hostname.get());
        if (port.get() != null && !port.get().isEmpty()) {
            stringBuilder.append(':');
            stringBuilder.append(port.get());
        }
        if (!username.get().isEmpty()) {
            stringBuilder.append(" --proxy-user=");
            stringBuilder.append(username.get());
        }
        if (!password.get().isEmpty()) {
            stringBuilder.append(" --proxy-password=");
            stringBuilder.append(password.get());
        }
        stringBuilder.append(' ');
        wgetProxy = stringBuilder.toString();
    }

    private void setupAptGetProxy() {
        StringBuilder stringBuilder = new StringBuilder();
        stringBuilder.append(" -o Acquire::http::proxy=http://");
        if (!username.get().isEmpty()) {
            stringBuilder.append(username.get());
            if (!password.get().isEmpty()) {
                stringBuilder.append(':');
                stringBuilder.append(password.get());
            }
            stringBuilder.append('@');
        }
        stringBuilder.append(hostname.get());
        if (port.get() != null && !port.get().isEmpty()) {
            stringBuilder.append(':');
            stringBuilder.append(port.get());
        }
        stringBuilder.append(' ');
        aptGetProxy = stringBuilder.toString();
    }

    @Override
    public Task<Boolean> newTask() {
        return new InternalTask();
    }

    private class InternalTask extends Task<Boolean> {

        @Override
        protected Boolean call() throws Exception {
            if (proxyActive.get()) {
                setupWgetProxy();
                setupAptGetProxy();
            }
            return true;
        }
    }

    public BooleanProperty getProxyActive() {
        return proxyActive;
    }

    public StringProperty getHostname() {
        return hostname;
    }

    public StringProperty getPort() {
        return port;
    }

    public StringProperty getUsername() {
        return username;
    }

    public StringProperty getPassword() {
        return password;
    }
    
    

}
