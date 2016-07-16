package com.gunjan.c3p0.btracescript;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.exception.ExceptionUtils;

import com.sun.btrace.BTraceUtils;
import com.sun.btrace.annotations.BTrace;
import com.sun.btrace.annotations.Kind;
import com.sun.btrace.annotations.Location;
import com.sun.btrace.annotations.OnMethod;
import com.sun.btrace.annotations.OnTimer;
import com.sun.btrace.annotations.Return;
import com.sun.btrace.annotations.Sampled;
import com.sun.btrace.annotations.Self;
import com.sun.btrace.annotations.Where;

@BTrace(unsafe = true)
class ConnectionLeakBTraceScript {

	private Map<Integer, String> leakInfo = new HashMap<Integer, String>();

	@OnMethod(clazz = "com.mchange.v2.resourcepool.BasicResourcePool", method = "checkoutResource")
	@Sampled(kind = Sampled.Sampler.Const, mean = 100)
	void func(@Self Object obj) {

		@SuppressWarnings("unchecked")
		HashMap<Object, Object> managedMap = (HashMap<Object, Object>) BTraceUtils.get(BTraceUtils.field("com.mchange.v2.resourcepool.BasicResourcePool", "managed"), obj);
		for (Map.Entry<Object, Object> entry : managedMap.entrySet()) {
			Object pc = entry.getValue();
			String oldPc = leakInfo.get(pc.hashCode());
			long last_checkin_time = (Long) BTraceUtils.get(BTraceUtils.field("com.mchange.v2.resourcepool.BasicResourcePool$PunchCard", "last_checkin_time"), pc);
			long checkout_time = (Long) BTraceUtils.get(BTraceUtils.field("com.mchange.v2.resourcepool.BasicResourcePool$PunchCard", "checkout_time"), pc);
			if (last_checkin_time <= checkout_time) {
				Exception checkoutStackTraceException = (Exception) BTraceUtils.get(BTraceUtils.field("com.mchange.v2.resourcepool.BasicResourcePool$PunchCard", "checkoutStackTraceException"), pc);
				if (oldPc == null && checkoutStackTraceException != null) {
					leakInfo.put(
							pc.hashCode(),
							"checkout_time==>  " + new Date(checkout_time).toString() + "  last_checkin_time==> " + new Date(last_checkin_time).toString() + " "
									+ ExceptionUtils.getStackTrace(checkoutStackTraceException));
				}
			} else {
				if (oldPc != null) {
					leakInfo.remove(pc.hashCode());
				}
			}
		}
	}

	@OnMethod(clazz = "com.mchange.v2.resourcepool.BasicResourcePool", method = "checkoutResource", location = @Location(value = Kind.RETURN, where = Where.BEFORE))
	void func1(@Self Object basicResPool, @Return Object connection) throws IllegalArgumentException, IllegalAccessException, NoSuchFieldException, SecurityException, ClassNotFoundException {
		@SuppressWarnings("unchecked")
		HashMap<Object, Object> managedMap = (HashMap<Object, Object>) BTraceUtils.get(BTraceUtils.field("com.mchange.v2.resourcepool.BasicResourcePool", "managed"), basicResPool);
		Object obj = managedMap.get(connection);
		Object punchCard = Class.forName("com.mchange.v2.resourcepool.BasicResourcePool$PunchCard").cast(obj);
		Field field = punchCard.getClass().getDeclaredField("checkoutStackTraceException");
		field.set(punchCard, new Exception("c3p0 connection checkout stack trace"));
	}

	@OnTimer(30000)
	public void ontime() {
		BTraceUtils.println("CHECKING LEAK");
		if (leakInfo.size() == 0) {
			BTraceUtils.println("NO LEAK");
		} else {
			BTraceUtils.println("LEAK IDENTIFIED");
			BTraceUtils.println(leakInfo);
		}

	}
}
