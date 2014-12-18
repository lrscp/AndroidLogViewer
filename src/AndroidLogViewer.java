import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import lrscp.lib.Log;
import lrscp.lib.Preference;
import lrscp.lib.swt.SwtUtils;

import org.apache.commons.io.FileUtils;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;
import org.eclipse.swt.dnd.DropTarget;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ControlEvent;
import org.eclipse.swt.events.ControlListener;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.KeyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Color;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

public class AndroidLogViewer extends Shell {
    private static class LogClient implements Runnable {
        private static final int MAX_LINES = 10000;
        private List<LogInfo> mClientLogs = new LinkedList<LogInfo>();
        private LogListener mLsnr;
        private String mName = "";
        private Socket mSocket;
        private boolean mPause = false;
        private boolean isReadingLines = false;

        public LogClient(Socket socket) {
            mSocket = socket;
        }

        public List<LogInfo> getLogs() {
            return mClientLogs;
        }

        public String getName() {
            return mName;
        }

        @Override
        public void run() {
            Log.d(TAG, "start client " + mSocket);
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(mSocket.getInputStream(), "utf-8"));
                isReadingLines = true;
                mName = br.readLine();
                if (shell != null) {
                    shell.updateClientsView();
                }
                String line = null;
                int i = 0;
                isReadingLines = true;
                while ((line = br.readLine()) != null) {
                    isReadingLines = false;
                    while (mPause) {
                        isReadingLines = true;
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {}
                    }
                    LogInfo info = new LogInfo(line, i++);
                    synchronized (this) {
                        if (mClientLogs.size() < MAX_LINES) {
                            mClientLogs.add(info);
                        } else {
                            mClientLogs.remove(0);
                            mClientLogs.add(info);
                        }
                    }
                    if (mLsnr != null) {
                        mLsnr.onLogAdded(this, info);
                    }
                    isReadingLines = true;
                }
            } catch (IOException e) {
                e.printStackTrace();
                mClients.remove(this);
                if (shell != null) {
                    shell.updateClientsView();
                }
            }
        }

        public void setLogListener(LogListener lsnr) {
            mLsnr = lsnr;
        }

        public void start() {
            new Thread(this).start();
        }

        /**
         * Stop receiving new log items until resume is called. Blocked until
         * the thread is paused.
         */
        public synchronized void pause() {
            mPause = true;
            while (!isReadingLines) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {}
            }
        }

        /**
         * Start receiving new log items.
         */
        public void resume() {
            mPause = false;
        }

        public synchronized void clearLogs() {
            mClientLogs.clear();
        }
    }

    private static interface LogListener {
        void onLogAdded(LogClient client, LogInfo info);
    }

    private static class LogServerThread implements Runnable {
        public LogServerThread() {}

        @Override
        public void run() {
            ServerSocket server = null;
            try {
                server = new ServerSocket(Log.LOG_VIEWER_PORT);
                while (true) {
                    Socket socket = server.accept();
                    LogClient client = new LogClient(socket);
                    mClients.add(client);
                    client.start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static class TimeRecorder {
        private static TimeRecorder sInstance = null;

        public static TimeRecorder getInstance() {
            if (sInstance == null) {
                sInstance = new TimeRecorder();
            }
            return sInstance;
        }

        private long mTableLoadStart;

        public long setTableLoadEnd() {
            return System.currentTimeMillis() - mTableLoadStart;
        }

        public void setTableLoadStart() {
            mTableLoadStart = System.currentTimeMillis();
        }
    }

    private static final String TAG = AndroidLogViewer.class.getSimpleName();

    private static Display display;
    private static List<LogClient> mClients = new ArrayList<AndroidLogViewer.LogClient>();
    private static Thread mLogServerThread = null;
    private static AndroidLogViewer shell;

    private static boolean isServerPortInUse() {
        try {
            ServerSocket server = new ServerSocket(Log.LOG_VIEWER_PORT);
            server.close();
            return false;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
    }

    /**
     * Launch the application.
     * 
     * @param args
     */
    public static void main(String args[]) {
        try {
            display = Display.getDefault();
            if (isServerPortInUse()) {
                MessageBox msg = new MessageBox(new Shell());
                msg.setText("Warning");
                msg.setMessage("AndroidLogViewer is running!");
                msg.open();
                Display.getDefault().readAndDispatch();
                System.exit(0);
                return;
            }
            startLogServer();
            shell = new AndroidLogViewer(display);
            shell.open();
            shell.layout();
            while (!shell.isDisposed()) {
                if (!display.readAndDispatch()) {
                    display.sleep();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "exception in main " + e.getLocalizedMessage());
        }
        System.exit(0);
    }

    private static void startLogServer() {
        if (mLogServerThread == null) {
            mLogServerThread = new Thread(new LogServerThread());
            mLogServerThread.start();
        }
    }

    private Combo cbLevel;
    private Composite cpLeft;
    private org.eclipse.swt.widgets.List list;
    private File mCurFile;

    private Set<LogInfo> mFastLocateItems = new HashSet<LogInfo>();

    private List<LogInfo> mFileLogs = new LinkedList<LogInfo>();

    /** The final log items that will be shown in the tabel **/
    private List<LogInfo> mFilteredLogs = new LinkedList<LogInfo>();

    private LogListener mLogLsnr = new LogListener() {
        @Override
        public void onLogAdded(final LogClient client, final LogInfo log) {
            getDisplay().asyncExec(new Runnable() {
                @Override
                public void run() {
                    if (mCurClient == client) {
                        onTableLogAdded(log);
                    } else {
                        // Log.e(TAG, "onLogAdded diff client!");
                    }
                }
            });
        }
    };

    private List<LogInfo> mLogs = new LinkedList<LogInfo>();

    Listener mLsnr = new Listener() {
        public void handleEvent(Event event) {
            TableItem item = (TableItem) event.item;
            int index = event.index;
            LogInfo log = mFilteredLogs.get(index);
            showLog(item, index, log);
        }
    };

    private boolean mScrollEnabled;

    private Table table;

    private TableColumn tblclmnContent;

    private TableColumn tblclmnIndex;

    private TableColumn tblclmnLevel;

    private TableColumn tblclmnPid;

    private TableColumn tblclmnTag;

    private TableColumn tblclmnTid;

    private TableColumn tblclmnTime;

    private Text text;
    private Button btnCheckButton;

    /** Update the client's log count thread **/
    private Thread mUpdateClientsCountThread;

    private LogClient mCurClient;
    private Composite composite_1;

    private org.eclipse.swt.widgets.List mFilterListView;

    private List<FilterConfig> mFilterConfigs = new ArrayList<FilterConfig>();
    private Button butnClear;

    /**
     * Create the shell.
     * 
     * @param display
     */
    public AndroidLogViewer(Display display) {
        super(display, SWT.SHELL_TRIM);
        setLayout(new GridLayout(2, false));

        cpLeft = new Composite(this, SWT.NONE);
        cpLeft.setLayout(new GridLayout(1, false));
        GridData gd_cpLeft = new GridData(SWT.FILL, SWT.FILL, false, false, 1, 2);
        gd_cpLeft.widthHint = 241;
        cpLeft.setLayoutData(gd_cpLeft);

        Label lblLog = new Label(cpLeft, SWT.NONE);
        lblLog.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        lblLog.setText("Clients");

        list = new org.eclipse.swt.widgets.List(cpLeft, SWT.BORDER | SWT.RESIZE);
        GridData gd_list = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
        gd_list.widthHint = 198;
        list.setLayoutData(gd_list);

        Label label = new Label(cpLeft, SWT.NONE);
        label.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
        label.setText("Filters");

        composite_1 = new Composite(cpLeft, SWT.NONE);
        composite_1.setLayout(new GridLayout(3, false));
        composite_1.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1));

        Button butnAddFilter = new Button(composite_1, SWT.NONE);
        butnAddFilter.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                createAddFilterDialog();
            }
        });
        butnAddFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        butnAddFilter.setBounds(0, 0, 92, 30);
        butnAddFilter.setText("Add");

        Button butnDelFilter = new Button(composite_1, SWT.NONE);
        butnDelFilter.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = mFilterListView.getSelectionIndex();
                if (index > 0) {
                    createDelFilterDialog(index - 1);
                }
            }
        });
        butnDelFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        butnDelFilter.setText("Delete");

        Button butnEditFilter = new Button(composite_1, SWT.NONE);
        butnEditFilter.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                int index = mFilterListView.getSelectionIndex();
                if (index > 0) {
                    createEditFilterDialog(index - 1);
                }
            }
        });
        butnEditFilter.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        butnEditFilter.setText("Edit");

        list.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onSelectClient(list.getSelectionIndex());
            }
        });
        updateClientsView();
        startUpdateClientLogCountThread();

        mFilterListView = new org.eclipse.swt.widgets.List(cpLeft, SWT.BORDER);
        mFilterListView.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                loadTableByCurrentFilter();
            }
        });
        mFilterListView.setLayoutData(new GridData(SWT.FILL, SWT.FILL, false, true, 1, 1));

        Composite composite = new Composite(this, SWT.NONE);
        composite.setLayout(new GridLayout(4, false));
        composite.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));

        text = new Text(composite, SWT.BORDER);
        text.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false, 1, 1));
        text.addKeyListener(new KeyListener() {
            @Override
            public void keyPressed(KeyEvent e) {}

            @Override
            public void keyReleased(KeyEvent e) {
                // press Enter
                if (e.keyCode == 13) {
                    loadTableByCurrentFilter();
                }
            }
        });

        btnCheckButton = new Button(composite, SWT.CHECK);
        btnCheckButton.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                mScrollEnabled = btnCheckButton.getSelection();
                scrollToBottomIfNeeded();
            }
        });
        btnCheckButton.setText("scroll");
        btnCheckButton.setSelection(true);
        mScrollEnabled = btnCheckButton.getSelection();

        butnClear = new Button(composite, SWT.NONE);
        butnClear.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(SelectionEvent e) {
                onClearLogs();
            }
        });
        butnClear.setText("Clear");

        cbLevel = new Combo(composite, SWT.NONE);
        GridData gd_combo = new GridData(SWT.FILL, SWT.CENTER, false, false, 1, 1);
        gd_combo.widthHint = 60;
        cbLevel.setLayoutData(gd_combo);
        cbLevel.setItems(LogInfo.levelStrings);
        cbLevel.select(0);
        cbLevel.addSelectionListener(new SelectionListener() {
            @Override
            public void widgetDefaultSelected(SelectionEvent e) {}

            @Override
            public void widgetSelected(SelectionEvent e) {
                loadTableByCurrentFilter();
            }
        });

        table = new Table(this, SWT.BORDER | SWT.FULL_SELECTION | SWT.MULTI | SWT.VIRTUAL);
        table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));
        table.setHeaderVisible(true);
        table.setLinesVisible(true);
        table.setMenu(createPopupMenu(new Menu(this, SWT.POP_UP)));
        createDropTarget();

        /*
         * NOTE: MeasureItem, PaintItem and EraseItem are called repeatedly.
         * Therefore, it is critical for performance that these methods be as
         * efficient as possible.
         */
        // Listener paintListener = new Listener() {
        // public void handleEvent(Event event) {
        // switch (event.type) {
        // case SWT.MeasureItem: {
        // TableItem item = (TableItem) event.item;
        // String text = item.getText(event.index);
        // Point size = event.gc.textExtent(text);
        // event.width = size.x;
        // event.height = Math.max(event.height, size.y);
        // break;
        // }
        // case SWT.PaintItem: {
        // TableItem item = (TableItem) event.item;
        // String text = item.getText(event.index);
        // Point size = event.gc.textExtent(text);
        // int offset2 = event.index == 0 ? Math.max(0, (event.height - size.y)
        // / 2) : 0;
        // event.gc.drawText(text, event.x, event.y + offset2, true);
        // break;
        // }
        // case SWT.EraseItem: {
        // event.detail &= ~SWT.FOREGROUND;
        // break;
        // }
        // }
        // }
        // };
        // table.addListener(SWT.MeasureItem, paintListener);
        // table.addListener(SWT.PaintItem, paintListener);
        // table.addListener(SWT.EraseItem, paintListener);

        tblclmnIndex = new TableColumn(table, SWT.NONE);
        tblclmnIndex.setWidth(Preference.getInt("indexColumnW", 60));
        tblclmnIndex.setText("index");
        tblclmnIndex.addControlListener(createControlLsnr("indexColumnW", tblclmnIndex));

        tblclmnLevel = new TableColumn(table, SWT.NONE);
        tblclmnLevel.setWidth(Preference.getInt("levelColumnW", 41));
        tblclmnLevel.setText("Level");
        tblclmnLevel.addControlListener(createControlLsnr("levelColumnW", tblclmnLevel));

        tblclmnTime = new TableColumn(table, SWT.NONE);
        tblclmnTime.setWidth(Preference.getInt("timeColumnW", 93));
        tblclmnTime.setText("Time");
        tblclmnTime.addControlListener(createControlLsnr("timeColumnW", tblclmnTime));

        tblclmnPid = new TableColumn(table, SWT.NONE);
        tblclmnPid.setWidth(Preference.getInt("pidColumnW", 40));
        tblclmnPid.setText("Pid");
        tblclmnPid.addControlListener(createControlLsnr("pidColumnW", tblclmnPid));

        tblclmnTid = new TableColumn(table, SWT.NONE);
        tblclmnTid.setWidth(Preference.getInt("tidColumnW", 39));
        tblclmnTid.setText("Tid");
        tblclmnTid.addControlListener(createControlLsnr("tidColumnW", tblclmnTid));

        tblclmnTag = new TableColumn(table, SWT.NONE);
        tblclmnTag.setWidth(Preference.getInt("tagColumnW", 108));
        tblclmnTag.setText("Tag");
        tblclmnTag.addControlListener(createControlLsnr("tagColumnW", tblclmnTag));

        tblclmnContent = new TableColumn(table, SWT.NONE);
        tblclmnContent.setWidth(Preference.getInt("contentColumnW", 10000));
        tblclmnContent.setText("Content");
        tblclmnContent.addControlListener(createControlLsnr("contentColumnW", tblclmnContent));

        createContents();
    }

    protected void onClearLogs() {
        if (mCurClient != null) {
            mCurClient.clearLogs();
            loadTableByCurrentFilter();
        }
    }

    protected void createEditFilterDialog(int index) {
        FilterConfig f = mFilterConfigs.get(index);
        FilterEditDialog dlg = new FilterEditDialog(shell, 0);
        dlg.setFilterConfig(f);
        String[] r = (String[]) dlg.open();
        if (r != null) {
            f = new FilterConfig();
            f.name = r[0];
            f.tag = r[1];
            mFilterConfigs.set(index, f);
            mFilterListView.setItem(index, f.name);
            saveFilters();
            loadTableByCurrentFilter();
        }
    }

    private void saveFilters() {
        StringBuilder sb = new StringBuilder();
        for (FilterConfig f : mFilterConfigs) {
            sb.append(f.name);
            sb.append(',');
            sb.append(f.tag);
            sb.append(' ');
        }
        Preference.setString("mFilterConfigs", sb.toString());
    }

    private void loadFilters() {
        String s = Preference.getString("mFilterConfigs", "");
        for (String seg : s.split(" ")) {
            if (!seg.isEmpty()) {
                String[] segs = seg.split(",");
                FilterConfig f = new FilterConfig();
                f.name = segs[0];
                f.tag = segs[1];
                mFilterConfigs.add(f);
            }
        }
        updateFilterList();
    }

    protected void createDelFilterDialog(int index) {
        if (SwtUtils.messageOkCancel(shell, "Warning", "Are you sure to delete this filter? " + mFilterConfigs.get(index).name)) {
            mFilterConfigs.remove(index);
            mFilterListView.remove(index, index);
            saveFilters();
            loadTableByCurrentFilter();
        }
    }

    protected void createAddFilterDialog() {
        FilterEditDialog dlg = new FilterEditDialog(shell, 0);
        String[] r = (String[]) dlg.open();
        if (r != null) {
            FilterConfig f = new FilterConfig();
            f.name = r[0];
            f.tag = r[1];
            mFilterConfigs.add(f);
            mFilterListView.add(f.name);
            saveFilters();
        }
    }

    private void updateFilterList() {
        mFilterListView.removeAll();
        mFilterListView.add("All messages");
        for (FilterConfig f : mFilterConfigs) {
            mFilterListView.add(f.name);
        }
    }

    private void startUpdateClientLogCountThread() {
        mUpdateClientsCountThread = new Thread(new Runnable() {
            @Override
            public void run() {
                while (true) {
                    updateClientsView();
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                }
            }
        });
        mUpdateClientsCountThread.start();
    }

    @Override
    protected void checkSubclass() {
        // Disable the check that prevents subclassing of SWT components
    }

    private void copySelectedItems() {
        StringBuilder sb = new StringBuilder();
        for (TableItem ti : table.getSelection()) {
            sb.append(((LogInfo) ti.getData()).originalLine);
            sb.append("\n");
        }

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Clipboard clipboard = toolkit.getSystemClipboard();
        StringSelection stringSel = new StringSelection(sb.toString());
        clipboard.setContents(stringSel, null);
    }

    /**
     * Create contents of the shell.
     */
    protected void createContents() {
        setText("SWT Application");
        setSize(1211, 732);
        SwtUtils.center(this, 0);
        setFile(Preference.getString("path", ""));
        loadFilters();
    }

    private ControlListener createControlLsnr(final String key, final TableColumn tableColumn) {
        return new ControlListener() {
            @Override
            public void controlMoved(ControlEvent e) {}

            @Override
            public void controlResized(ControlEvent e) {
                Preference.setInt(key, tableColumn.getWidth());
            }
        };
    }

    private DropTarget createDropTarget() {
        DropTarget dropTarget = new DropTarget(table, DND.DROP_MOVE | DND.DROP_COPY | DND.DROP_LINK);
        dropTarget.setTransfer(new Transfer[] { FileTransfer.getInstance() });
        dropTarget.addDropListener(new DropTargetAdapter() {
            public void dragEnter(DropTargetEvent event) {}

            public void dragLeave(DropTargetEvent event) {}

            public void dragOver(DropTargetEvent event) {}

            public void drop(DropTargetEvent event) {
                String[] files = (String[]) event.data;
                if (files != null && files.length == 1) {
                    setFile(files[0]);
                }
            }
        });
        return dropTarget;
    }

    private Menu createPopupMenu(Menu popUpMenu) {
        for (MenuItem mi : popUpMenu.getItems()) {
            mi.dispose();
        }

        final MenuItem itemCopy = new MenuItem(popUpMenu, SWT.PUSH);
        itemCopy.setText("Copy Log");
        itemCopy.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                if (table.getSelection().length > 0) {
                    copySelectedItems();
                }
            }
        });
        new MenuItem(popUpMenu, SWT.SEPARATOR);

        // final MenuItem itemEdit = new MenuItem(popUpMenu, SWT.PUSH);
        // itemEdit.setText("截取后面的Log并保存");
        // itemEdit.addSelectionListener(new SelectionAdapter() {
        // public void widgetSelected(SelectionEvent e) {
        // if (table.getSelection().length > 0) {
        // FileDialog dlg = new FileDialog(getShell());
        // dlg.setFilterExtensions(new String[] { "*.txt", "*.log" });
        // String filePath = dlg.open();
        // Log.i("path " + filePath);
        // if (filePath != null) {
        // FileWriter fw;
        // try {
        // fw = new FileWriter(filePath);
        // int i = 0;
        // int index = table.getSelectionIndex();
        // for (LogInfo log : mLogs) {
        // if (i < index) {} else {
        // fw.write(log.originalLine);
        // fw.write('\n');
        // }
        // i++;
        // }
        // fw.close();
        // } catch (IOException e1) {
        // e1.printStackTrace();
        // }
        //
        // setFile(filePath);
        // }
        // }
        // }
        // });
        // new MenuItem(popUpMenu, SWT.SEPARATOR);

        final MenuItem itemAddFastLocate = new MenuItem(popUpMenu, SWT.PUSH);
        itemAddFastLocate.setText("Add to fast location list");
        itemAddFastLocate.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                if (table.getSelection().length > 0) {
                    TableItem ti = table.getSelection()[0];
                    LogInfo log = (LogInfo) ti.getData();
                    mFastLocateItems.add(log);
                    createPopupMenu(table.getMenu());
                }
            }
        });
        new MenuItem(popUpMenu, SWT.SEPARATOR);

        for (final LogInfo log : mFastLocateItems) {
            final MenuItem item = new MenuItem(popUpMenu, SWT.PUSH);

            // cut the long string
            String s = log.content;
            if (s.length() > 100) {
                s = s.substring(0, 100);
            }
            item.setText(s);

            item.addSelectionListener(new SelectionAdapter() {
                public void widgetSelected(SelectionEvent e) {
                    scrollToItem(log);
                }
            });
        }

        final MenuItem item = new MenuItem(popUpMenu, SWT.PUSH);
        item.setText("Clear fast location list");
        item.addSelectionListener(new SelectionAdapter() {
            public void widgetSelected(SelectionEvent e) {
                mFastLocateItems.clear();
                createPopupMenu(table.getMenu());
            }
        });

        new MenuItem(popUpMenu, SWT.SEPARATOR);

        return popUpMenu;
    }

    private LogFilter getCurLogFilter() {
        String regx = text.getText();
        int index = mFilterListView.getSelectionIndex();
        if (index > 0) {
            FilterConfig f = mFilterConfigs.get(index - 1);
            regx += "tag:" + f.tag + " ";
        }
        return new LogFilter(LogInfo.levelStrings[cbLevel.getSelectionIndex()], regx);
    }

    private Color getLevelColor(String level) {
        Color c = getDisplay().getSystemColor(SWT.COLOR_BLACK);
        if (level.equals("I")) {
            c = new Color(null, 0x00, 0x99, 0x00);
        } else if (level.equals("V")) {
            c = new Color(null, 0x66, 0x66, 0x66);
        } else if (level.equals("D")) {
            c = new Color(null, 0x44, 0x33, 0x33);
        } else if (level.equals("W")) {
            c = new Color(null, 0xf7, 0x97, 0x09);
        } else if (level.equals("E")) {
            c = new Color(null, 0xbb, 0x00, 0x00);
        }
        if (level.equals("F")) {
            c = new Color(null, 0xaa, 0x00, 0x00);
        }
        return c;
    }

    private List<LogInfo> getLogSource() {
        return mLogs;
    }

    private String getTitle() {
        StringBuilder sb = new StringBuilder();
        sb.append("Android Log Viewer By lrscp");
        if (mCurFile != null) {
            sb.append(" -- ");
            sb.append(mCurFile.getAbsolutePath());
        }
        return sb.toString();
    }

    void loadFile(String path) {
        try {
            long mStartTime = System.currentTimeMillis();
            // parse lines
            mFileLogs.clear();
            List<String> lines = FileUtils.readLines(new File(path), "utf-8");
            int index = 0;
            for (String line : lines) {
                if (line.isEmpty()) {
                    continue;
                }
                LogInfo info = new LogInfo(line, index);
                mFileLogs.add(info);
                index++;
            }
            Log.d(TAG, "load file time " + (System.currentTimeMillis() - mStartTime));

            setLogSource(mFileLogs);
            // show log items
            loadTableByCurrentFilter();
        } catch (IOException e) {
            e.printStackTrace();
        } finally {}
    }

    /**
     * Clear the table item and load log items filtered by the current filter.
     * 
     * @param filter
     */
    public void loadTable(LogFilter filter) {
        table.removeAll();
        mFilteredLogs.clear();
        for (final LogInfo log : getLogSource()) {
            if (!filter.consume(log)) {
                mFilteredLogs.add(log);
            }
        }
        // parse time closeness
        // for (LogInfo log : mFilteredLogs) {
        // long time = log.timeMillis;
        // // TODO
        // }
        table.setItemCount(mFilteredLogs.size());
        table.removeListener(SWT.SetData, mLsnr);
        table.addListener(SWT.SetData, mLsnr);
        // for (LogInfo log : mFilteredLogs) {
        // Log.d(TAG, log);
        // }
    }

    // show the table according to current filter setting
    protected void loadTableByCurrentFilter() {
        loadTable(getCurLogFilter());
    }

    protected void onSelectClient(int index) {
        Log.d(TAG, "onSelectClient " + index);

        mCurClient = mClients.get(index);

        // stop receiving new logs until log listener is set.
        mCurClient.pause();

        // reset LogListener
        for (LogClient c : mClients) {
            c.setLogListener(null);
        }

        setLogSource(mCurClient.getLogs());

        loadTableByCurrentFilter();

        mCurClient.setLogListener(mLogLsnr);

        mCurClient.resume();
    }

    private void scrollToBottomIfNeeded() {
        if (mScrollEnabled) {
            int index = mFilteredLogs.size() - 1;
            table.setTopIndex(index);
        }
    }

    private void scrollToItem(LogInfo log) {
        if (getCurLogFilter().consume(log)) {
            // item is not showing in the table
            showMsg("The log is not found!");
            return;
        }

        int index = 0;
        for (LogInfo logCur : mFilteredLogs) {
            Log.d(TAG, "logCur " + logCur + " log " + log);
            if (logCur.id == log.id) {
                // table.showItem(ti);
                table.select(index);
                table.setTopIndex(index);
                return;
            }
            index++;
        }

    }

    protected void setFile(String path) {
        Log.d(TAG, "set file " + path);
        Preference.setString("path", path);
        mCurFile = new File(path);
        setText(getTitle());
        loadFile(path);
    }

    /**
     * Change log source, the source can be socket clients or file lines
     * 
     * @param logs
     */
    private void setLogSource(List<LogInfo> logs) {
        mLogs = logs;
    }

    protected void showLog(TableItem ti, int index, LogInfo info) {
        ti.setForeground(getLevelColor(info.logLevel));
        ti.setText(new String[] { "" + index, info.logLevel, info.time, info.pid, info.tid, info.tag, info.content });
        ti.setData(info);
    }

    private void showMsg(String text) {
        MessageBox msg = new MessageBox(getShell());
        msg.setMessage(text);
        msg.open();
    }

    public void updateClientsView() {
        getDisplay().asyncExec(new Runnable() {
            @Override
            public void run() {
                // Log.d(TAG, "updateClientsView");
                int sel = list.getSelectionIndex();
                list.removeAll();
                for (LogClient c : mClients) {
                    String name = null;
                    if (c.getName().isEmpty()) {
                        name = "?";
                    } else {
                        name = c.getName();
                    }
                    name += "(" + c.getLogs().size() + ")";
                    list.add(name);
                }
                if (sel < list.getItemCount()) {
                    list.select(sel);
                } else {
                    list.select(0);
                }
            }
        });
    }

    /**
     * Scroll the table to the bottom when a new item is added.
     * 
     * @param log
     */
    private void onTableLogAdded(LogInfo log) {
        LogFilter filter = getCurLogFilter();
        if (!filter.consume(log)) {
            mFilteredLogs.add(log);
        }
        table.setItemCount(mFilteredLogs.size());
        scrollToBottomIfNeeded();
    }
}
