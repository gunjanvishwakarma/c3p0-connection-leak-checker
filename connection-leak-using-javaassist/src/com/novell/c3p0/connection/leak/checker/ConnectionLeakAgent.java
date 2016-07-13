package com.novell.c3p0.connection.leak.checker;

import java.lang.instrument.Instrumentation;

public class ConnectionLeakAgent {
	public static void premain(String args, Instrumentation inst) {
		System.out.println("Starting the agent");
		inst.addTransformer(new C3POClassTransformer());
	}
}
