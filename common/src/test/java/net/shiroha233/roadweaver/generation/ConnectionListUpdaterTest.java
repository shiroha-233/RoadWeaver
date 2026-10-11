package net.shiroha233.roadweaver.generation;

import net.minecraft.core.BlockPos;
import net.shiroha233.roadweaver.core.model.ConnectionStatus;
import net.shiroha233.roadweaver.core.model.StructureConnection;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectionListUpdaterTest {
    private static final BlockPos FROM = new BlockPos(1, 64, 2);
    private static final BlockPos TO = new BlockPos(30, 64, 40);

    @Test
    void claimsPlannedAndResumesGeneratingButNeverResurrectsTerminalConnections() {
        List<StructureConnection> connections = new ArrayList<>(List.of(connection(ConnectionStatus.PLANNED)));
        StructureConnection reversed = new StructureConnection(TO, FROM);

        assertEquals(ConnectionStatus.GENERATING,
                ConnectionListUpdater.transitionIn(connections, reversed, ConnectionStatus.GENERATING,
                        ConnectionStatus.PLANNED, ConnectionStatus.GENERATING).orElseThrow().status());
        assertTrue(ConnectionListUpdater.transitionIn(connections, reversed, ConnectionStatus.GENERATING,
                ConnectionStatus.PLANNED, ConnectionStatus.GENERATING).isPresent());
        assertEquals(ConnectionStatus.COMPLETED,
                ConnectionListUpdater.transitionIn(connections, reversed, ConnectionStatus.COMPLETED,
                        ConnectionStatus.GENERATING).orElseThrow().status());
        assertTrue(ConnectionListUpdater.transitionIn(connections, reversed, ConnectionStatus.FAILED,
                ConnectionStatus.GENERATING).isEmpty());
        assertTrue(ConnectionListUpdater.transitionIn(connections, reversed, ConnectionStatus.GENERATING,
                ConnectionStatus.PLANNED, ConnectionStatus.GENERATING).isEmpty());
        assertEquals(ConnectionStatus.COMPLETED, connections.getFirst().status());
    }

    @Test
    void removedOrFailedConnectionsAreNotWrittenBackByLateTasks() {
        List<StructureConnection> connections = new ArrayList<>(List.of(connection(ConnectionStatus.FAILED)));
        assertTrue(ConnectionListUpdater.transitionIn(connections, connection(ConnectionStatus.PLANNED),
                ConnectionStatus.COMPLETED, ConnectionStatus.GENERATING).isEmpty());
        assertEquals(ConnectionStatus.GENERATING,
                ConnectionListUpdater.transitionIn(connections, connection(ConnectionStatus.PLANNED),
                        ConnectionStatus.GENERATING, ConnectionStatus.PLANNED, ConnectionStatus.FAILED)
                        .orElseThrow().status());
        connections.clear();
        assertTrue(ConnectionListUpdater.transitionIn(connections, connection(ConnectionStatus.PLANNED),
                ConnectionStatus.GENERATING, ConnectionStatus.PLANNED).isEmpty());
        assertTrue(connections.isEmpty());
    }

    private static StructureConnection connection(ConnectionStatus status) {
        return new StructureConnection(FROM, TO, status);
    }
}
