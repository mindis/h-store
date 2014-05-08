/* This file is part of VoltDB.
 * Copyright (C) 2008-2012 VoltDB Inc.
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
 * OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
 * ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
 * OTHER DEALINGS IN THE SOFTWARE.
 */

//
// Accepts a vote, enforcing business logic: make sure the vote is for a valid
// contestant and that the voterdemosstore (phone number of the caller) is not above the
// number of allowed votes.
//

package edu.brown.benchmark.voterdemohstoreanother.procedures;

import org.voltdb.ProcInfo;
import org.voltdb.SQLStmt;
import org.voltdb.StmtInfo;
import org.voltdb.VoltProcedure;
import org.voltdb.VoltTable;
import org.voltdb.types.TimestampType;

import edu.brown.benchmark.voterdemohstoreanother.VoterDemoHStoreAnotherConstants;

@ProcInfo (
	//partitionInfo = "contestants.contestant_number:1",
    singlePartition = true
)
public class GenerateLeaderboard extends VoltProcedure {
	
	////////////////////////////first batch of SQL statements/////////////////////////////
    // Put vote into leaderboard
	public final SQLStmt getCurrentTS = new SQLStmt(
	   "SELECT ts FROM proc_one_out;"
    );
	
    public final SQLStmt trendingLeaderboardStmt = new SQLStmt(
	   "INSERT INTO w_staging (vote_id, phone_number, state, contestant_number, ts) SELECT * FROM proc_one_out;"
    );
    
    public final SQLStmt clearProcOut = new SQLStmt(
    	"DELETE FROM proc_one_out;"	
    );
    
    /**
    public final SQLStmt updateCountingWin = new SQLStmt(
    	"INSERT INTO counting_win (vote_id) SELECT vote_id FROM proc_one_out;"	
    );
    */
    
    /**
    public final SQLStmt checkStagingTimestamp = new SQLStmt(
    	"SELECT MAX(ts) FROM w_staging;"	
    );
    
    public final SQLStmt checkWindowTimestamp = new SQLStmt(
    	"SELECT MIN(ts) FROM w_trending_leaderboard;"	
    );*/
    
    public final SQLStmt checkWindowTimestamp = new SQLStmt(
    	"SELECT minTS FROM minWindow WHERE row_id = 1;"	
    );
    
    public final SQLStmt updateWindowTS = new SQLStmt(
        "UPDATE minWindow SET minTS = ? WHERE row_id = 1;"
    );
    
    @StmtInfo(
            upsertable=true
        )
    public final SQLStmt updateCount = new SQLStmt(
    	"INSERT INTO voteCount (row_id, cnt) SELECT row_id, cnt + 1 FROM voteCount WHERE row_id = 1;"
    );
    
    @StmtInfo(
            upsertable=true
        )
    public final SQLStmt updateTotalCount = new SQLStmt(
    	"INSERT INTO totalLeaderboardCount (row_id, cnt) SELECT row_id, cnt + 1 FROM totalLeaderboardCount WHERE row_id = 1;"
    );
    
    public final SQLStmt getCount = new SQLStmt(
    	"SELECT cnt FROM voteCount;"
    );
    
    /**
    //hack to avoid views
    public final SQLStmt clearLowestContestant = new SQLStmt(
    	"DELETE FROM votes_by_contestant;"
    );
    
  //hack to avoid views
    public final SQLStmt setLowestContestants = new SQLStmt(
    	//"SELECT contestant_number, num_votes FROM v_contestant_count ORDER BY num_votes ASC LIMIT 1;"
    	"INSERT INTO votes_by_contestant (contestant_number,num_votes) SELECT contestant_number, count(*) FROM votes GROUP BY contestant_number;"
    );
    */
    
    public final SQLStmt getLowestContestant = new SQLStmt(
    	"SELECT contestant_number, num_votes FROM v_votes_by_contestant ORDER BY num_votes ASC LIMIT 1;"
    );
    
    ////////////////////////////second batch of SQL statements/////////////////////////////////////////
    public final SQLStmt slideWindow1 = new SQLStmt(
    	"DELETE FROM w_trending_leaderboard WHERE ts < ?;"
    );
    
    public final SQLStmt slideWindow2 = new SQLStmt(
    	"INSERT INTO w_trending_leaderboard (vote_id, phone_number, state, contestant_number, ts) SELECT * FROM w_staging;"
    );
    
    public final SQLStmt clearStaging = new SQLStmt(
    	"DELETE FROM w_staging;"
    );
    
    public final SQLStmt deleteLeaderboard = new SQLStmt(
            "DELETE FROM top_three_last_30_sec;"
    );

    public final SQLStmt updateLeaderboard = new SQLStmt(
            "INSERT INTO top_three_last_30_sec (contestant_number, num_votes) SELECT contestant_number, count(*) FROM w_trending_leaderboard GROUP BY contestant_number;"
    );
    
    
    ////////////////////////////third batch of SQL statements//////////////////////////////////////////
    public final SQLStmt deleteContestant = new SQLStmt(
    	"DELETE FROM contestants WHERE contestant_number = ?;"	
    );
    
    public final SQLStmt deleteVotes = new SQLStmt(
        "DELETE FROM votes WHERE contestant_number = ?;"	
    );
    
    public final SQLStmt deleteFromStaging = new SQLStmt(
        	"DELETE FROM w_staging WHERE contestant_number = ?;"	
        );
    
    public final SQLStmt deleteFromWindow = new SQLStmt(
        	"DELETE FROM w_trending_leaderboard WHERE contestant_number = ?;"	
        );
    
    public final SQLStmt deleteFromLeaderboard = new SQLStmt(
    	"DELETE FROM top_three_last_30_sec WHERE contestant_number = ?;"	
    );
    
    public final SQLStmt resetCount = new SQLStmt(
    	"UPDATE voteCount SET cnt = 0;"	
    );
    
    public final SQLStmt getTopLeaderboard = new SQLStmt(
    	"SELECT contestant_number, num_votes FROM v_votes_by_contestant ORDER BY num_votes DESC LIMIT 3;"	
    );
    
    public final SQLStmt getBottomLeaderboard = new SQLStmt(
    	"SELECT contestant_number, num_votes FROM v_votes_by_contestant ORDER BY num_votes ASC LIMIT 3;"	
    );
    
    public final SQLStmt getTrendingLeaderboard = new SQLStmt(
    	"SELECT contestant_number, num_votes FROM v_top_three_last_30_sec ORDER BY num_votes DESC LIMIT 3;"	
    );
    

	
public long run(int currentTimestamp) {
	
		//voltQueueSQL(getCurrentTS);
		voltQueueSQL(checkWindowTimestamp);
        voltQueueSQL(updateCount);
        voltQueueSQL(getCount);
        voltQueueSQL(updateTotalCount);
        VoltTable validation[] = voltExecuteSQL();
        
        //long currentTimestamp = validation[0].fetchRow(0).getLong(0);
        int minWinTimestamp = (int)validation[0].fetchRow(0).getLong(0);
        int voteCount = (int)validation[1].fetchRow(0).getLong(0);
        
        if(currentTimestamp - minWinTimestamp >= VoterDemoHStoreAnotherConstants.WINDOW_SIZE + VoterDemoHStoreAnotherConstants.SLIDE_SIZE)
        {
        	voltQueueSQL(updateWindowTS, currentTimestamp - VoterDemoHStoreAnotherConstants.WINDOW_SIZE);
        	voltQueueSQL(slideWindow1, currentTimestamp - VoterDemoHStoreAnotherConstants.WINDOW_SIZE);
        	voltQueueSQL(slideWindow2);
        	voltQueueSQL(clearStaging);
        	voltQueueSQL(deleteLeaderboard);
        	voltQueueSQL(updateLeaderboard);
        }
        voltQueueSQL(trendingLeaderboardStmt);
        voltQueueSQL(clearProcOut);
        voltExecuteSQL();
        
        // check the number of votes so far
        if ( voteCount >= VoterDemoHStoreAnotherConstants.VOTE_THRESHOLD) {
        	
        	voltQueueSQL(getLowestContestant);
        	validation = voltExecuteSQL();
        	long contestant_number = validation[0].fetchRow(0).getLong(0);
        	
        	voltQueueSQL(deleteVotes, contestant_number);
        	voltQueueSQL(deleteFromWindow, contestant_number);
        	voltQueueSQL(deleteFromStaging, contestant_number);
        	voltQueueSQL(deleteFromLeaderboard, contestant_number);
        	voltQueueSQL(deleteContestant, contestant_number);
            voltQueueSQL(resetCount);
        }
        voltQueueSQL(getTrendingLeaderboard);
        voltQueueSQL(getTopLeaderboard);
        voltQueueSQL(getBottomLeaderboard);
        voltExecuteSQL(true);
        // Set the return value to 0: successful vote
        return VoterDemoHStoreAnotherConstants.VOTE_SUCCESSFUL;
    }
}