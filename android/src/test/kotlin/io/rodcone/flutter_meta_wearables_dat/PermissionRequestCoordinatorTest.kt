package io.rodcone.flutter_meta_wearables_dat

import kotlin.test.*
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.jupiter.api.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PermissionRequestCoordinatorTest {
    @Test fun `camera and microphone requests serialize and preserve grant denial`() = runTest {
        val coordinator = PermissionRequestCoordinator<Boolean>()
        val codes = mutableListOf<Int>()
        val camera = async { coordinator.serialized { coordinator.request { codes.add(it) } } }
        val microphone = async { coordinator.serialized { coordinator.request { codes.add(it) } } }
        runCurrent(); assertEquals(1, codes.size)
        coordinator.complete(codes[0], true); runCurrent()
        assertEquals(2, codes.size); assertNotEquals(codes[0], codes[1])
        coordinator.complete(codes[1], false)
        assertTrue(camera.await()); assertFalse(microphone.await())
    }
    @Test fun `timeout unlocks queue and late callback cannot complete next request`() = runTest {
        val coordinator = PermissionRequestCoordinator<Boolean>(100)
        val codes = mutableListOf<Int>()
        val first = async { runCatching { coordinator.serialized { coordinator.request { codes.add(it) } } } }
        val second = async { coordinator.serialized { coordinator.request { codes.add(it) } } }
        runCurrent(); advanceTimeBy(100); runCurrent()
        assertIs<TimeoutCancellationException>(first.await().exceptionOrNull())
        coordinator.complete(codes[0], true); assertFalse(second.isCompleted)
        coordinator.complete(codes[1], false); assertFalse(second.await())
    }
    @Test fun `activity loss fails request and releases mutex`() = runTest {
        val coordinator = PermissionRequestCoordinator<Boolean>()
        val first = async { runCatching { coordinator.serialized { coordinator.request { } } } }
        runCurrent(); coordinator.abort("Activity detached")
        assertIs<IllegalStateException>(first.await().exceptionOrNull())
        assertTrue(coordinator.serialized { true })
    }
    @Test fun `cancelled request and duplicate callbacks are ignored`() = runTest {
        val coordinator = PermissionRequestCoordinator<Boolean>()
        var code = 0
        val first = async { coordinator.serialized { coordinator.request { code = it } } }
        runCurrent(); first.cancelAndJoin()
        assertFalse(coordinator.accepts(code)); coordinator.complete(code, true)
        val second = async { coordinator.serialized { coordinator.request { code = it } } }
        runCurrent(); coordinator.complete(code, true); coordinator.complete(code, false)
        assertTrue(second.await())
    }
    @Test fun `failed activity launch does not strand the next request`() = runTest {
        val coordinator = PermissionRequestCoordinator<Boolean>()
        val failure = runCatching { coordinator.serialized { coordinator.request { error("No activity") } } }
        assertIs<IllegalStateException>(failure.exceptionOrNull())
        assertTrue(coordinator.serialized { true })
    }
}
