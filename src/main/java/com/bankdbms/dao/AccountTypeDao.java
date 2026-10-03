package com.bankdbms.dao;

import com.bankdbms.db.JdbcSupport;
import com.bankdbms.model.AccountCategory;
import com.bankdbms.model.AccountType;
import com.bankdbms.model.EntrySide;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public class AccountTypeDao {

    private static final String COLUMNS = "account_type_id, type_code, type_name, category, normal_side, "
            + "min_opening_balance, min_balance, max_holders, interest_rate, description";

    public int insert(Connection conn, AccountType t) throws SQLException {
        String sql = "INSERT INTO account_type (type_code, type_name, category, normal_side, min_opening_balance, "
                + "min_balance, max_holders, interest_rate, description) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        return (int) JdbcSupport.insertReturningKey(conn, sql, ps -> {
            ps.setString(1, t.typeCode());
            ps.setString(2, t.typeName());
            ps.setString(3, t.category().name());
            ps.setString(4, t.normalSide().name());
            ps.setBigDecimal(5, t.minOpeningBalance());
            ps.setBigDecimal(6, t.minBalance());
            ps.setInt(7, t.maxHolders());
            ps.setBigDecimal(8, t.interestRate());
            JdbcSupport.setNullableString(ps, 9, t.description());
        });
    }

    public Optional<AccountType> findById(Connection conn, int id) throws SQLException {
        return JdbcSupport.queryOne(conn, "SELECT " + COLUMNS + " FROM account_type WHERE account_type_id = ?",
                ps -> ps.setInt(1, id), AccountTypeDao::map);
    }

    public List<AccountType> findAll(Connection conn) throws SQLException {
        return JdbcSupport.queryList(conn, "SELECT " + COLUMNS + " FROM account_type ORDER BY account_type_id",
                JdbcSupport.NO_PARAMS, AccountTypeDao::map);
    }

    public List<AccountType> findByCategory(Connection conn, AccountCategory category) throws SQLException {
        return JdbcSupport.queryList(conn,
                "SELECT " + COLUMNS + " FROM account_type WHERE category = ? ORDER BY account_type_id",
                ps -> ps.setString(1, category.name()), AccountTypeDao::map);
    }

    private static AccountType map(ResultSet rs) throws SQLException {
        return new AccountType(rs.getInt("account_type_id"), rs.getString("type_code"), rs.getString("type_name"),
                AccountCategory.valueOf(rs.getString("category")), EntrySide.valueOf(rs.getString("normal_side")),
                rs.getBigDecimal("min_opening_balance"), rs.getBigDecimal("min_balance"), rs.getInt("max_holders"),
                rs.getBigDecimal("interest_rate"), rs.getString("description"));
    }
}
