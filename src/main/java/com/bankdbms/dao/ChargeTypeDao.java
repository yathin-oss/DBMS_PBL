package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.ChargeType;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class ChargeTypeDao {

    private static final String COLUMNS = "charge_type_id, charge_code, charge_name, default_amount";

    public List<ChargeType> findAll(Connection conn) throws SQLException {
        return JdbcSupport.queryList(conn, "SELECT " + COLUMNS + " FROM charge_type ORDER BY charge_type_id",
                JdbcSupport.NO_PARAMS, ChargeTypeDao::map);
    }

    public Optional<ChargeType> findById(Connection conn, int id) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM charge_type WHERE charge_type_id = ?",
                ps -> ps.setInt(1, id), ChargeTypeDao::map);
    }

    private static ChargeType map(ResultSet rs) throws SQLException {
        return new ChargeType(rs.getInt("charge_type_id"), rs.getString("charge_code"), rs.getString("charge_name"),
                rs.getBigDecimal("default_amount"));
    }
}
