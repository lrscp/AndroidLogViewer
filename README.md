AndroidLogViewer
================

A tool for android developers to help them view log files more efficient.<br>
I am an android developer and it is always annoying me when a bug issue contains several log files which is so large and contains more than 10000 lines of log. Reading these log file is a hard job. As google didn't offer a tool for reading android log files, so I have to develop my own one. I started to modify DDMS's logcat tool and added some useful features, so that I can use this tool to improve my work efficiency. If you have suffered the same problem, you can just use my tool. Just download and enter into directory **out**, click **run.bat** and that's over.

Overview
--------
![](https://raw.githubusercontent.com/lrscp/AndroidLogViewer/master/pics/p1.jpg)

Functions
---------
- **Parse android log file and show them in a colorful way, similar to the DDMS's logcat. The difference is this can show log file which DDMS's logcat can't do.**
- **Filt log items, so that you will find desired log more efficiently.**
- **Copy log item or items.**
- **Add log item to fast location list.**<br>
  This is a very useful function when you want to locate the log item when you change your filter or some other situations.
- **Listening for log clients using tcp and show logs in real time.**<br>
  This is a simple protocol for my application development. Using an unified tool to manage logs from many applications that I developed.


Contact me
----------
* lrscp(675486378@qq.com)