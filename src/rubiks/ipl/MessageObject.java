package rubiks.ipl;


import ibis.ipl.ReceivePortIdentifier;

import java.io.Serializable;

/**
 * Class which represents message object
 * Should be used to be sent from client to server
 * Can possibly contain Job stealing request or returning value from one job execution
 */
public class MessageObject implements Serializable{
   enum message_id{ JOB_STEALING, JOB_RESULT, JOB_CUBE, JOB_INFORM, EMPTY_MESSAGE};

   public message_id messageType = message_id.EMPTY_MESSAGE; // by default
   public Serializable data = null; // by default
   public ReceivePortIdentifier requestor;

   public String toString(){
      StringBuilder res = new StringBuilder();
      res.append("MessageObject{messageType: ");
      if(messageType == message_id.JOB_STEALING)
         res.append("JOB_STEALING");
      else if(messageType == message_id.JOB_CUBE)
         res.append("JOB_CUBE");
      else if(messageType == message_id.JOB_INFORM)
         res.append("JOB_INFORM");
      else
         res.append("SOLUTIONS_NUM");
      res.append("; data:");
      
	if(data != null)
		res.append(data.toString());
	else
		res.append("null");
      res.append(";}");
      return res.toString();
   }
}
