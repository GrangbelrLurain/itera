package aerocore.backend.repository;

import aerocore.backend.model.ChannelCommission;

import java.util.List;
import java.util.Optional;

public interface ChannelCommissionRepository {

    /** No commissions at all (DIRECT sales, tests). */
    ChannelCommissionRepository NONE = new ChannelCommissionRepository() {
        @Override public List<ChannelCommission> findAll() { return List.of(); }
        @Override public void replaceAll(List<ChannelCommission> rows) { throw new UnsupportedOperationException(); }
    };

    List<ChannelCommission> findAll();

    /** Replaces every commission row (the admin edits the whole table at once). */
    void replaceAll(List<ChannelCommission> rows);

    /** Active policy for the channel: the product-specific row first, then the channel's all-products row. */
    default Optional<ChannelCommission> resolve(String channelCode, String productCode) {
        List<ChannelCommission> all = findAll();
        Optional<ChannelCommission> specific = all.stream()
                .filter(c -> c.active() && c.channelCode().equals(channelCode) && productCode.equals(c.productCode()))
                .findFirst();
        if (specific.isPresent()) return specific;
        return all.stream()
                .filter(c -> c.active() && c.channelCode().equals(channelCode) && c.productCode() == null)
                .findFirst();
    }
}
