# Requires usage of the tool btrace-release-1.3 (https://github.com/btraceio/btrace)
su -c "btrace-release-1.3/bin/btrace -u -v -cp /opt/novell/zenworks/java/lib/commons-lang-2.6.jar 15202 ConnectionLeakBTraceScript.java" -s /bin/bash zenworks
