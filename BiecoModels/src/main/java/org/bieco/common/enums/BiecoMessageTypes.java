package org.bieco.common.enums;

public abstract class BiecoMessageTypes {
    //CONTROL CLASS
    public static final String START = "Start";
    public static final String STOP = "Stop";
    public static final String HALT = "Halt";
    public static final String CONFIGURE = "Configure";
    public static final String HEARTBEAT = "Heartbeat";
    public static final String GETSTATUS = "GetStatus";

    //STATUS CLASS
    public static final String INITIALIZING = "Initializing";
    public static final String ONLINE = "Online";
    public static final String CONFIGURING = "Configuring";
    public static final String READY = "Ready";
    public static final String RUNNING = "Running";
    public static final String FINISHING = "Finishing";
    public static final String FINISHED = "Finished";
    public static final String HALTING = "Halting";
    public static final String HALTED = "Halted";
    public static final String ERROR = "Error";
    public static final String EXCEPTION = "Exception";
    public static final String OFFLINE = "Offline";

    //DATA CLASS
    public static final String DATA = "Data";
    public static final String EVENT = "Event";

    //REQUEST CLASS
    public static final String STORE = "Store";
    public static final String RETRIEVE = "Retrieve";
    public static final String APIACCESS = "APIAccess";
    public static final String DISPLAY = "Display";
    public static final String IFRAME = "Iframe";
    public static final String IFRAMEEND = "IframeEnd";

    //LOG CLASS
    public static final String INFO = "Info";
    public static final String NOTICE = "Notice";
    public static final String WARNING = "Warning";

    //UI Class
    public static final String UI = "UI";
    public static final String UIREQUEST = "UiRequest";

}
