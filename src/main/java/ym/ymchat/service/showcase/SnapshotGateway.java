package ym.ymchat.service.showcase;

import java.util.concurrent.atomic.AtomicLong;

public interface SnapshotGateway {

    String createInventorySnapshot(ShowcaseSource source, long now);

    String createEnderChestSnapshot(ShowcaseSource source, long now);

    static SnapshotGateway noop() {
        AtomicLong counter = new AtomicLong();
        return new SnapshotGateway() {
            @Override
            public String createInventorySnapshot(ShowcaseSource source, long now) {
                return "inventory-" + counter.incrementAndGet();
            }

            @Override
            public String createEnderChestSnapshot(ShowcaseSource source, long now) {
                return "ender-chest-" + counter.incrementAndGet();
            }
        };
    }
}
