package edu.brown.benchmark.tpceb.generators;

import java.lang.reflect.Method;
import java.util.Date;

import edu.brown.benchmark.tpceb.generators.TradeGenerator.TradeType;
import edu.brown.benchmark.tpceb.util.EGenRandom;

public class MEETradingFloor {
    
    public long  getRNGSeed(){
        return( rnd.getSeed() );
    }
    
    public void  setRNGSeed( long RNGSeed ){
        rnd.setSeed( RNGSeed );
    }
    
    public MEETradingFloor( MEESUTInterface  sut, MEEPriceBoard  priceBoard, MEETickerTape  tickerTape, Date  baseTime, Date  currentTime ){
        this.sut = sut ;
        this.priceBoard = priceBoard;
        this.tickerTape = tickerTape;
        this.baseTime = baseTime;
        this.currentTime = currentTime;
        rnd =  new EGenRandom(EGenRandom.RNG_SEED_BASE_MEE_TRADING_FLOOR );
        orderProcessingDelayMean = 1.0;
        Method SendTradeResult = null;
            try{
                System.out.println("in try");
                SendTradeResult = MEETradingFloor.class.getMethod("sendTradeResult", TTradeRequest.class);
            }catch(Exception e){
                e.printStackTrace();
            }
            System.out.println("calling orderTimers");
            
        orderTimers = new TimerWheel(TTradeRequest.class, this, SendTradeResult, 5, 1);
        System.out.println("done with trade orders");
     }
        
    
     public MEETradingFloor(MEESUTInterface  sut, MEEPriceBoard  priceBoard, MEETickerTape  tickerTape, Date  baseTime, Date  currentTime, long RNGSeed){
         this.sut = sut ;
         this.priceBoard = priceBoard;
         this.tickerTape = tickerTape;
         this.baseTime = baseTime;
         this.currentTime = currentTime;
         rnd =  new EGenRandom(RNGSeed );
         orderProcessingDelayMean = 1.0;
     }
     
     private double  genProcessingDelay(double mean){
        double result = ( -1.0 * Math.log( rnd.rndDouble() )) * mean;
    
        if( result > maxOrderProcessingDelay ){
            return( maxOrderProcessingDelay );
        }
        else{
            return result;
        }
    }
    
     public int  submitTradeRequest( TTradeRequest tradeReq ){
        switch( tradeReq.eAction ){
        case eMEEProcessOrder:
            {
                System.out.println("in eMEEProcessOrder - fails");
                return( orderTimers.startTimer( genProcessingDelay( orderProcessingDelayMean )));
            }
        case eMEESetLimitOrderTrigger:
            System.out.println("going into post limit order");
            tickerTape.PostLimitOrder( tradeReq );
            System.out.println("out of post limit order");
          //  sendTradeResult(tradeReq); //added for debugging

            return( orderTimers.processExpiredTimers() );
        default:
            return( orderTimers.processExpiredTimers() );
        }
    }
    
    public int  generateTradeResult(){
        return( orderTimers.processExpiredTimers() );
    }
    
    public void  sendTradeResult( TTradeRequest tradeReq ){
        System.out.println("trying to send trade result");
        TradeType            eTradeType;
        TTradeResultTxnInput    txnInput = new TTradeResultTxnInput();
        TTickerEntry            TickerEntry = new TTickerEntry();
        double                  CurrentPrice = -1.0;
    
        eTradeType = tickerTape.ConvertTradeTypeIdToEnum( tradeReq.trade_type_id.toCharArray() );
        CurrentPrice = priceBoard.getCurrentPrice( tradeReq.symbol ).getDollars();
    
        txnInput.trade_id = tradeReq.trade_id;
        System.out.println("Trade id:" + txnInput.trade_id);
       // txnInput.st_completed_id =  "E_COMPLETED";
    
        if(( eTradeType == TradeType.eLimitBuy && tradeReq.price_quote < CurrentPrice )||( eTradeType == TradeType.eLimitSell && tradeReq.price_quote > CurrentPrice )){
            txnInput.trade_price = tradeReq.price_quote;
        }
        else{
            txnInput.trade_price = CurrentPrice;
        }
    
        sut.TradeResult(  txnInput );
    
        TickerEntry.symbol = new String( tradeReq.symbol);
        TickerEntry.trade_qty = tradeReq.trade_qty;
    
        TickerEntry.price_quote = CurrentPrice;
    
        tickerTape.AddEntry(TickerEntry);
    }
    
    private MEESUTInterface                                        sut;
    private MEEPriceBoard                                          priceBoard;
    private MEETickerTape                                          tickerTape;

    private Date                                                      baseTime;
    private Date                                                      currentTime;

    private TimerWheel                                               orderTimers;
    private EGenRandom                                             rnd;
    private  double                                                orderProcessingDelayMean;
    private static final int                                       maxOrderProcessingDelay = 5;

    public static final int  NO_OUTSTANDING_TRADES = -1;
}