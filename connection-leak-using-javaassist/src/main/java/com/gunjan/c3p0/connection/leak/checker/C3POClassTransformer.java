package com.gunjan.c3p0.connection.leak.checker;

import java.io.ByteArrayInputStream;
import java.lang.instrument.ClassFileTransformer;
import java.lang.instrument.IllegalClassFormatException;
import java.security.ProtectionDomain;

import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtMethod;
import javassist.CtNewMethod;
import javassist.LoaderClassPath;

public class C3POClassTransformer implements ClassFileTransformer {

	public byte[] transform(ClassLoader loader, String className, Class<?> classBeingRedefined, ProtectionDomain protectionDomain,
			byte[] classfileBuffer) throws IllegalClassFormatException {

		byte[] result = null;

		try {
			if ("com/mchange/v2/resourcepool/BasicResourcePool".equals(className)) {
				System.out.println("============BYTE CODE MANIPULATION STATED============= " + className);
				ClassPool pool = new ClassPool();
				pool.appendClassPath(new LoaderClassPath(loader));
				CtClass basicResPool = pool.makeClass(new ByteArrayInputStream(classfileBuffer));

				if (!basicResPool.isFrozen()) {

					String runMethodSrc = "public void run() {	"
							+ "			try { 		"
							+ "				while (true) { "
							+ "					System.out.println(\"*****************************************************CHECKING CONNECTION LEAK....THIS CHECK WILL RUN EVERY 5 MINS*****************************************************\");"
							+ "					System.out.println(\"Thread Name::\"+Thread.currentThread().getName());"
							+ "					Thread.sleep(5000L); "
							+ "					for (java.util.Iterator iter = this.managed.entrySet().iterator(); iter.hasNext();) {"
							+ "						java.util.Map.Entry entry = (java.util.Map.Entry) iter.next();"
							+ "						com.mchange.v2.resourcepool.BasicResourcePool$PunchCard pc = (com.mchange.v2.resourcepool.BasicResourcePool$PunchCard) entry.getValue();"
							+ " 					if(pc.last_checkin_time <= pc.checkout_time){"
							+ "							System.out.println(\"********************************************PRINTING PUNCH CARD INFO START**************************************************************\");"
							+ "							System.out.println(\"acquisition_time=\"+ pc.acquisition_time + \" ==> \" + new java.util.Date(pc.acquisition_time));"
							+ "							System.out.println(\"last_checkin_time=\"+ pc.last_checkin_time + \" ==> \" + new java.util.Date(pc.last_checkin_time));"
							+ "							System.out.println(\"checkout_time=\"+ pc.checkout_time + \" ==> \" + new java.util.Date(pc.checkout_time));"
							+ "							if(pc.checkoutStackTraceException != null) System.out.println(\"checkoutStackTrace=\"+  org.apache.commons.lang.exception.ExceptionUtils.getStackTrace(pc.checkoutStackTraceException));"
							+ "							System.out.println(\"********************************************PRINTING PUNCH CARD INFO END**************************************************************\");"
							+ "						}" + "					}" + "				}" + "			} catch (Exception e) {" + "				e.printStackTrace();" + "			}" + "		}" + "	}";

					CtMethod runMethod = CtNewMethod.make(runMethodSrc, basicResPool);
					basicResPool.addMethod(runMethod);

					basicResPool.addInterface(pool.get("java.lang.Runnable"));

					CtConstructor[] ctConstructors = basicResPool.getConstructors();

					for (CtConstructor ctConstructor : ctConstructors) {
						ctConstructor.insertAfter("{ new Thread(this).start(); }");
					}

					CtMethod cmethod = basicResPool.getDeclaredMethod("checkoutResource", new CtClass[] { CtClass.longType });
					cmethod.insertAt(
							586,
							"{Exception exception = new Exception(\"Connection Leak Identified\"); exception.setStackTrace(Thread.currentThread().getStackTrace()); card.checkoutStackTraceException = exception;}");
				}
				result = basicResPool.toBytecode();
				System.out.println("============BYTE CODE MANIPULATION ENDED============= " + className);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return result;
	}
}
