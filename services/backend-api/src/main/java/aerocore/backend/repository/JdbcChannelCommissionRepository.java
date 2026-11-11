package aerocore.backend.repository;

import aerocore.backend.model.ChannelCommission;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** SQL Server implementation. Table: infra/db/006-pricing.sql (product_code '*' = every product). */
public class JdbcChannelCommissionRepository implements ChannelCommissionRepository {

    private static final String ALL = "*";

    private final String url;
    private final String user;
    private final String password;

    public JdbcChannelCommissionRepository(String url, String user, String password) {
        this.url = url;
        this.user = user;
        this.password = password;
    }

    @Override
    public List<ChannelCommission> findAll() {
        List<ChannelCommission> out = new ArrayList<>();
        try (Connection c = DriverManager.getConnection(url, user, password);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM dbo.channel_commission ORDER BY channel_code, product_code")) {
            while (rs.next()) {
                String product = rs.getString("product_code");
                out.add(new ChannelCommission(rs.getString("channel_code"), ALL.equals(product) ? null : product,
                        rs.getInt("commission_bp"), rs.getLong("commission_fixed"), rs.getBoolean("active")));
            }
            return out;
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to load channel commissions: " + e.getMessage(), e);
        }
    }

    @Override
    public void replaceAll(List<ChannelCommission> rows) {
        try (Connection c = DriverManager.getConnection(url, user, password)) {
            c.setAutoCommit(false);
            try {
                try (Statement st = c.createStatement()) {
                    st.executeUpdate("DELETE FROM dbo.channel_commission");
                }
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO dbo.channel_commission (channel_code, product_code, commission_bp, "
                                + "commission_fixed, active) VALUES (?,?,?,?,?)")) {
                    for (ChannelCommission r : rows) {
                        ps.setString(1, r.channelCode());
                        ps.setString(2, r.productCode() == null ? ALL : r.productCode());
                        ps.setInt(3, r.commissionBp());
                        ps.setLong(4, r.commissionFixed());
                        ps.setBoolean(5, r.active());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                c.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                    // connection is closed right after
                }
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to save channel commissions: " + e.getMessage(), e);
        }
    }
}
