import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import lrscp.lib.Log;

public class LogInfo {
    // private static Pattern pat = Pattern.compile(
    // "^\\[\\s(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)"
    // + "\\s+(\\d*):\\s*(\\S+)\\s([VDIWEAF])/(.*)\\]$");
    private static Pattern PAT1 = Pattern.compile("(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s+(\\d*)\\s*(\\S+)\\s([VDIWEAF])\\s([^:]+):(.+)");
    private static Pattern PAT_TIME_MILLIS = Pattern.compile("()");
    private static Pattern PAT2 = Pattern.compile("([VDIWEAF])/([^:]+):(.*)");
    private static Pattern PAT3 = Pattern.compile("(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+): ([VDIWEAF])/([^\\(]+)\\((\\d+\\)):(.+)");
    private static Pattern PAT4 = Pattern.compile("(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d\\.\\d+)\\s([VDIWEAF])/([^\\(]+)\\(\\s*(\\d+)\\):(.+)");
    private static Pattern PAT5 = Pattern.compile("(\\d\\d-\\d\\d\\s\\d\\d:\\d\\d:\\d\\d):\\s([VDIWEAF])/([^\\(]+)\\(\\s*(\\d+)\\):(.+)");
    public static Pattern[] pats = new Pattern[] { PAT1, PAT2, PAT3, PAT4, PAT5 };

    public String time = "";
    public String pid = "";
    public String tid = "";
    public String logLevel = "";
    public String tag = "";
    public String content = "";
    public String originalLine = "";
    public int id = 0;
    public int timeGroup;
    public long timeMillis = 0;

    public static final Map<String, Integer> levelMap = new HashMap<String, Integer>();
    public static String[] levelStrings = new String[] { "V", "D", "I", "W", "E", "F" };
    static {
        levelMap.put("V", 0);
        levelMap.put("D", 1);
        levelMap.put("I", 2);
        levelMap.put("W", 3);
        levelMap.put("E", 4);
        levelMap.put("F", 4);
    }

    public LogInfo(String line, int index) {
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("line is empty");
        }
        originalLine = line;
        id = index;
        parse(line);
    }

    private long getTime(String time) {
        Matcher m = PAT_TIME_MILLIS.matcher(time);
        if (m.matches()) {
            int month = Integer.valueOf(m.group(1));
            int date = Integer.valueOf(m.group(2));
            int hourOfDay = Integer.valueOf(m.group(3));
            int minute = Integer.valueOf(m.group(4));
            int second = Integer.valueOf(m.group(5));
            int millis = Integer.valueOf(m.group(6));
            Calendar cal = Calendar.getInstance();
            cal.set(2000, month, date, hourOfDay, minute, second);
            return cal.getTimeInMillis() / 1000 * 1000 + millis;
        }
        return 0;
    }

    private void parse(String line) {
        for (int i = 0; i < pats.length; i++) {
            Pattern pat = pats[i];
            Matcher m = pat.matcher(line);
            if (!m.matches()) {
                continue;
            }
            if (pats[0] != pat) {
                // swap
                Pattern pTmp = pats[0];
                pats[0] = pat;
                pats[i] = pTmp;
            }
            if (pat == PAT1) {
                time = m.group(1);
                timeMillis = getTime(time);
                pid = m.group(2);
                tid = m.group(3);
                logLevel = m.group(4);
                checkLogLevel();
                tag = m.group(5).trim();
                content = m.group(6);

                /*
                 * LogLevel doesn't support messages with severity "F".
                 * Log.wtf() is supposed to generate "A", but generates "F".
                 */
                if (logLevel == null && m.group(4).equals("F")) {
                    logLevel = "F";
                }
            } else if (pat == PAT2) {
                time = "";
                timeMillis = 0;
                pid = "";
                tid = "";
                logLevel = m.group(1);
                checkLogLevel();
                tag = m.group(2).trim();
                content = m.group(3);
            } else if (pat == PAT3) {
                time = m.group(1);
                timeMillis = getTime(time);
                pid = m.group(4);
                logLevel = m.group(2);
                checkLogLevel();
                tag = m.group(3).trim();
                content = m.group(5);

                /*
                 * LogLevel doesn't support messages with severity "F".
                 * Log.wtf() is supposed to generate "A", but generates "F".
                 */
                if (logLevel == null && m.group(2).equals("F")) {
                    logLevel = "F";
                }
            } else if (pat == PAT4) {
                time = m.group(1);
                timeMillis = getTime(time);
                pid = m.group(4);
                logLevel = m.group(2);
                checkLogLevel();
                tag = m.group(3).trim();
                content = m.group(5);

                /*
                 * LogLevel doesn't support messages with severity "F".
                 * Log.wtf() is supposed to generate "A", but generates "F".
                 */
                if (logLevel == null && m.group(2).equals("F")) {
                    logLevel = "F";
                }
            } else if (pat == PAT5) {
                time = m.group(1);
                timeMillis = getTime(time);
                pid = m.group(4);
                logLevel = m.group(2);
                checkLogLevel();
                tag = m.group(3).trim();
                content = m.group(5);

                /*
                 * LogLevel doesn't support messages with severity "F".
                 * Log.wtf() is supposed to generate "A", but generates "F".
                 */
                if (logLevel == null && m.group(2).equals("F")) {
                    logLevel = "F";
                }
            }
            return;
        }

        content = line;
    }

    private void checkLogLevel() {
        if (!levelMap.containsKey(logLevel)) {
            Log.e("!levelMap.containsKey(logLevel)");
        }
    }

    @Override
    public String toString() {
        return String.format("mCurTime=%s,mCurPid=%s,mCurTid=%s,mCurTag=%s,mCurLogLevel=%s,msg=%s", time, pid, tid, tag, logLevel, content);
    }

    public static int compareLevel(String level1, String level2) {
        return levelMap.get(level1) - levelMap.get(level2);
    }
}
