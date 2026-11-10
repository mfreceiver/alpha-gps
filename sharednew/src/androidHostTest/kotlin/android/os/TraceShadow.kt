@file:Suppress("unused")

package android.os

/**
 * Host (JVM) test shadow for android.os.Trace. The mockable android.jar stub
 * throws "not mocked" from these methods, but the Compose runtime calls them
 * during composition/dispose (GapComposer). Real devices route these to the
 * native tracer; on the JVM they are safe no-ops. Test classes shadow the stub
 * because the test classes directory precedes android.jar on the classpath.
 */
object Trace {
    @JvmStatic fun beginSection(name: String?) {}
    @JvmStatic fun endSection() {}
    @JvmStatic fun beginAsyncSection(name: String?, cookie: Int) {}
    @JvmStatic fun endAsyncSection(name: String?, cookie: Int) {}
    @JvmStatic fun traceCounter(name: String?, counter: Int) {}
}
