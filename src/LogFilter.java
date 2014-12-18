import java.util.ArrayList;
import java.util.List;

import lrscp.lib.Log;

public class LogFilter {
    private static final String TAG = LogFilter.class.getSimpleName();

    private String mLogLevel;
    private String mRegx;
    private String tag;
    private String pid;
    private List<String> keywords = new ArrayList<String>();

    public LogFilter(String logLevel, String regx) {
        mLogLevel = logLevel;
        mRegx = regx;
        String[] segs = mRegx.split(" ");
        for (String s : segs) {
            if (s.startsWith("tag:")) {
                tag = s.substring(4);
            } else if (s.startsWith("pid:")) {
                pid = s.substring(4);
            } else {
                keywords.add(s);
            }
        }
//        Log.d(TAG, "tag=" + tag);
    }

    public boolean consume(LogInfo log) {
        try {
            if (log.tag.contains("DEBUG") || log.tag.contains("libc")) {
                return false;
            }

            if (pid != null && !log.pid.equals(pid)) {
                return true;
            }

            if (LogInfo.compareLevel(log.logLevel, mLogLevel) < 0) {
                return true;
            }

            if (tag != null && !log.tag.toLowerCase().contains(tag.toLowerCase())) {
                return true;
            }

            for (String keyword : keywords) {
                if (!log.content.toLowerCase().contains(keyword.toLowerCase())) {
                    return true;
                }
            }
        } catch (Exception e) {
            // Log.e("compare log level error:" + mLogLevel);
        }

        return false;
    }

}
