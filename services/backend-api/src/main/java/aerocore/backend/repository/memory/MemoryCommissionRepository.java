package aerocore.backend.repository.memory;

import aerocore.backend.model.ChannelCommission;
import aerocore.backend.repository.ChannelCommissionRepository;

import java.util.ArrayList;
import java.util.List;

/** Channel commissions kept in memory (profile "memory"). */
public class MemoryCommissionRepository implements ChannelCommissionRepository {

    private final List<ChannelCommission> rows = new ArrayList<>();

    @Override
    public synchronized List<ChannelCommission> findAll() {
        return new ArrayList<>(rows);
    }

    @Override
    public synchronized void replaceAll(List<ChannelCommission> newRows) {
        rows.clear();
        rows.addAll(newRows);
    }
}
