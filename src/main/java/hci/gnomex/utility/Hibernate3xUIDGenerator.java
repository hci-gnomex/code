//package hci.gnomex.utility;
//
//import java.io.IOException;
//import java.io.ObjectInputStream;
//import java.io.ObjectOutputStream;
//import java.io.Serializable;
//import java.sql.CallableStatement;
//import java.sql.ResultSet;
//import java.sql.SQLException;
//import java.sql.Types;
//
//import org.apache.commons.logging.Log;
//import org.apache.commons.logging.LogFactory;
//import org.hibernate.engine.spi.SessionImplementor;
//import org.hibernate.id.IdentifierGenerator;
//
///**
// * <p>Title: </p>
// * <p>Description: </p>
// * <p>Copyright: Copyright (c) 2003</p>
// * <p>Company: HCI - Informatics</p>
// * @author Kirt Henrie
// * @version 1.0
// */
//
//public class Hibernate3xUIDGenerator implements IdentifierGenerator, Serializable {
//
//  private static final Log LOG = LogFactory.getLog(Hibernate3xUIDGenerator.class);
//  private String table;
//
//  public Hibernate3xUIDGenerator(String table) {
//    this.table = table;
//  }
//  public Hibernate3xUIDGenerator() {
//  }
//  public Serializable generate(SessionImplementor session, Object obj) throws org.hibernate.HibernateException {
//    ResultSet rs = null;
//    CallableStatement stmt = null;
//    int id;
//
//    try {
//      stmt = session.connection().prepareCall("{ call dbo.pr_GetNextKey(?, ?, ?) }");
//
//      stmt.setString(1, table);
//      stmt.setInt(2, 0);
//      stmt.setInt(3, 0);
//      stmt.registerOutParameter(2, Types.INTEGER);
//
//      stmt.executeUpdate();
//
//      id = stmt.getInt(2);
//
//      LOG.debug("Sequence ID generated: " + id);
//      return new Integer(id);
//    }
//    catch (SQLException sqle) {
//            throw new RuntimeException( "Cannot get next id " + sqle.toString());
//    }
//    finally {
//      try {
//            stmt.close();
//      } catch (SQLException sqle) {
//        throw new RuntimeException( "Cannot close statement " + sqle.toString());
//      }
//    }
//  }
//  private void writeObject(ObjectOutputStream oos) throws IOException {
//    oos.defaultWriteObject();
//  }
//  private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
//    ois.defaultReadObject();
//  }
//  public String getTable() {
//    return table;
//  }
//  public void setTable(String table) {
//    this.table = table;
//  }
//}