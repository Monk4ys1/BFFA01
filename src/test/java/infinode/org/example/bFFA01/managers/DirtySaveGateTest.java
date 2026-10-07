package infinode.org.example.bFFA01.managers;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DirtySaveGateTest {

    @Test
    void repeatedMarksScheduleOnceUntilTheBatchIsTaken() {
        DirtySaveGate gate = new DirtySaveGate();

        assertTrue(gate.markAndShouldSchedule());
        assertFalse(gate.markAndShouldSchedule());
        assertTrue(gate.takeSnapshotRequest());
        assertFalse(gate.isDirty());
        assertFalse(gate.takeSnapshotRequest());

        assertTrue(gate.markAndShouldSchedule());
    }

    @Test
    void failedWriteCanBeMarkedDirtyAgain() {
        DirtySaveGate gate = new DirtySaveGate();
        gate.markAndShouldSchedule();
        gate.takeSnapshotRequest();

        gate.markDirtyAgain();

        assertTrue(gate.isDirty());
        assertTrue(gate.markAndShouldSchedule());
    }

    @Test
    void clearDropsAPendingBatch() {
        DirtySaveGate gate = new DirtySaveGate();
        gate.markAndShouldSchedule();
        gate.clear();

        assertFalse(gate.isDirty());
        assertTrue(gate.markAndShouldSchedule());
    }
}
