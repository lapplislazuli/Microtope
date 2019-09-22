package microtope.worker;

import java.io.Closeable;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import microtope.config.SqlConfig;
import microtope.messages.CoinMessage;
import microtope.messages.LoginMessage;
import microtope.messages.LogoutMessage;
import microtope.messages.StepMessage;

public class MariaDBWriter implements Closeable, DBWriter{
	
	Connection con;
	private static Logger logger = LogManager.getLogger(MariaDBWriter.class);
	private SqlConfig sqlconf;
	

	public MariaDBWriter(SqlConfig sqlconf){
		if(sqlconf.isEmpty())
			throw new IllegalArgumentException("Recieved Empty SQLConf!");
		this.sqlconf=sqlconf;
	}
	
	public void open(Connection con) throws SQLException {
		this.con=con;
		healthcheck();
	}
	
	protected Connection buildConnectionFromConfig() throws SQLException {
		var url = String.format("jdbc:mysql://%s:%s/%s",sqlconf.addressToConnect,sqlconf.portToConnect,sqlconf.databaseToConnect);
	    logger.debug("Trying to connect to "+url+" as "+sqlconf.userToConnect+ " with Password [REDACTED] ");
	    var con =  DriverManager.getConnection(url,sqlconf.userToConnect, sqlconf.passwordToConnect);
	    logger.info("Connection to " + url + " established");
	    return con;
	}
	
	private void healthcheck() throws SQLException {
		logger.info("performing healthcheck for mariadb writer");
		if(isOpenAndReady()) {
			PreparedStatement stmt = con.prepareStatement("SELECT status from health;");
			
			logger.debug("executing prepared statement for healthcheck...");
		    ResultSet rs=stmt.executeQuery();
		    logger.debug("recieved resultset ... recieved:");
		    
		    while(rs.next()){  
		    	logger.info(rs.getString("status"));  
		    }
		    logger.info("healthcheck passed!");
		}
	}


	@Override
	public void writeLogin(LoginMessage msg) {
		try {
			if(isOpenAndReady()) {
				writePlayer(msg.getPlayer_Id(),msg.getTeam_Id());
				logger.debug("Created Player - now inserting login");
				PreparedStatement stmt = con.prepareStatement("INSERT INTO audits (player_id, action, recorded) VALUES (? ,'login', ?);");
				
				stmt.setInt(1, msg.getPlayer_Id());
				stmt.setTimestamp(2, convertUtilToSql(msg.getTimeStamp()));
				
			    stmt.executeQuery();

				logger.debug("Inserted Login for player " + msg.getPlayer_Id());
			}
		} catch (SQLException e) {
			logger.error(e);
		}
	}

	@Override
	public void writeLogout(LogoutMessage msg) {
		try {
			if(isOpenAndReady()) {
				PreparedStatement stmt = con.prepareStatement("INSERT INTO audits (player_id, action, recorded) VALUES (? ,'logout', ?);");
				
				stmt.setInt(1, msg.getPlayer_Id());
				stmt.setTimestamp(2, convertUtilToSql(msg.getTimeStamp()));
				
			    stmt.executeQuery();

				logger.debug("Inserted Logout for player " + msg.getPlayer_Id());
			}
		} catch (SQLException e) {
			logger.error(e);
		}		
	}
	@Override
	public void writeSteps(StepMessage msg) {
		try {
			if(isOpenAndReady()) {
				PreparedStatement stmt = con.prepareStatement("INSERT INTO steps (player_id, steps, recorded) VALUES (? , ?, ?);");
				
				stmt.setInt(1, msg.getPlayer_Id());
				stmt.setInt(2, msg.getSteps());
				stmt.setTimestamp(3, convertUtilToSql(msg.getTimeStamp()));
				
			    stmt.executeQuery();

				logger.debug("Inserted " + msg.getSteps() + " steps for player " + msg.getPlayer_Id());
			}
		} catch (SQLException e) {
			logger.error(e);
		}
	}
	
	@Override
	public void writePlayer(int player_id, int team_id) {
		// This writes the player if it does not exist
		logger.debug("writing player " + player_id + " with team " + team_id);
		try {
			if(isOpenAndReady()) {
				PreparedStatement stmt = con.prepareStatement("INSERT IGNORE INTO players (player_id, team_id) VALUES (? , ?)");
				
				stmt.setInt(1, player_id);
				stmt.setInt(2, team_id);
				
			    stmt.executeQuery();
			    
			    logger.debug("Creating Player worked - not sure if player already existed!");
			}
		}catch(SQLException e) {
			logger.error("Recieved SQL Exception while Creating Player " + player_id,e);
		}
	}
	
	@Override
	public void writeCoins(CoinMessage msg) {
		try {
			if(isOpenAndReady()) {
				PreparedStatement stmt = con.prepareStatement("INSERT INTO coins (player_id, value, recorded) VALUES (? , ?, ?);");
				
				stmt.setInt(1, msg.getPlayer_Id());
				stmt.setInt(2, msg.getCoins());
				stmt.setTimestamp(3, convertUtilToSql(msg.getTimeStamp()));
				
			    stmt.executeQuery();

				logger.debug("Inserted " + msg.getCoins() + " coins for player " + msg.getPlayer_Id());
			}
		} catch (SQLException e) {
			logger.error(e);
		}
	}
	
	private boolean isOpenAndReady() {
		try {
			if(con==null || con.isClosed()) {
				logger.error("connection is null or closed!");
				return false;
			}
		} catch (SQLException e) {
			return false;
		}
		return true;
	}
	
	@Override
	public void close() throws IOException {
		try {
			con.close();
			logger.debug("Closed MariaDBWriter DB Connection successfully");
		} catch (SQLException e) {
			logger.error(e);
		} catch (NullPointerException ne) {
			logger.warn("Tried closing MariadbWriter-but was never open!");
		}
	}
	
	private java.sql.Timestamp convertUtilToSql(java.util.Date uDate) {
		return new java.sql.Timestamp(uDate.getTime());
    }
}
