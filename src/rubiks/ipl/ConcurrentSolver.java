package rubiks.ipl;

import ibis.ipl.*;

import java.io.IOException;
import java.net.CacheRequest;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class ConcurrentSolver implements MessageUpcall{
    public static final boolean PRINT_SOLUTION = false;
    private static final Integer MAX_HOPS = 1;

    /**
     * Port type is used for sending a request to the master
     */
    PortType requestPortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_AUTO_UPCALLS,
            PortType.CONNECTION_MANY_TO_ONE);

    /**
     * Port type is used for sending a reply back
     */
    PortType replyPortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_OBJECT, PortType.RECEIVE_EXPLICIT,
            PortType.CONNECTION_MANY_TO_ONE);


    /**
     * Port type is used for sending new found bound from the master to slave
     */
    PortType slaveBroadcastPortType = new PortType(PortType.COMMUNICATION_RELIABLE,
            PortType.SERIALIZATION_DATA, PortType.RECEIVE_AUTO_UPCALLS,
            PortType.CONNECTION_ONE_TO_MANY, PortType.CONNECTION_DOWNCALLS);

    /**
     * Ibis identifier of the master node
     */
    IbisIdentifier master = null;
    private long minimumBound = Integer.MAX_VALUE - MAX_HOPS;

    IbisCapabilities ibisCapabilities = new IbisCapabilities(
            IbisCapabilities.ELECTIONS_STRICT);
    private Ibis myIbis = null;
    private Queue<Cube> jobQueue;
    private int solutionsNum = 0;
    long solutionsStep = Integer.MAX_VALUE - MAX_HOPS;
    long startTime, endTime;
    long jobsTotal = 0;
    /**
     * Recursive function to find a solution for a given cube. Only searches to
     * the bound set in the cube object.
     *
     * @param cube
     *            cube to solve
     * @param cache
     *            cache of cubes used for new cube objects
     * @return the number of solutions found
     */
    private int solutions(Cube cube, CubeCache cache) {
        if (cube.isSolved()) {
            return 1;
        }

        if (cube.getTwists() >= cube.getBound()) {
            return 0;
        }

        // generate all possible cubes from this one by twisting it in
        // every possible way. Gets new objects from the cache
        Cube[] children = cube.generateChildren(cache);

        int result = 0;

        for (Cube child : children) {
            if(cube.getBound() > minimumBound){
                return -1; // dynamic pruning the job
            }
            // recursion step
            int childSolutions = solutions(child, cache);
            if (childSolutions > 0) {
                result += childSolutions;
                if (PRINT_SOLUTION) {
                    child.print(System.err);
                }
            }
            // put child object in cache
            cache.put(child);
        }

        return result;
    }

    /**
     * Solves a Rubik's cube by iteratively searching for solutions with a
     * greater depth. This guarantees the optimal solution is found. Repeats all
     * work for the previous iteration each iteration though...
     *
     * @param cube
     *            the cube to solve
     */
    private Pair<Integer, Integer> solve(Cube cube) {
        // cache used for cube objects. Doing new Cube() for every move
        // overloads the garbage collector
        CubeCache cache = new CubeCache(cube.getSize());
        int bound = 0;
        int result = 0;
        cube.dropTwists();
        System.out.print("Bound now:");

        while (result == 0) {
            bound++;
            cube.setBound(bound);
            if(bound > minimumBound){
                return new Pair<Integer, Integer>(0, 0); // pruning the job
            }
            System.err.print(" " + bound);
            result = solutions(cube, cache);
        }

        System.err.println();
        System.out.println("Solving cube possible in " + result + " ways of "
                + bound + " steps");
        if(result > 0)
            return new Pair<Integer, Integer>(result, bound);
        else
            return new Pair<Integer, Integer>(0, 0); // job is pruned
    }


    public void run(Cube cube) throws IbisCreationFailedException, IOException, ClassNotFoundException {
        myIbis = IbisFactory.createIbis(ibisCapabilities, null,
                requestPortType, replyPortType);
        master = myIbis.registry().elect("Master");
        if (master.equals(myIbis.identifier())) { //  I AM MASTER
            CubeCache cubeCache = new CubeCache((int) Math.pow(cube.getSize(), MAX_HOPS));
	        jobQueue = new ConcurrentLinkedQueue<Cube>(generateJobs(cube, MAX_HOPS, cubeCache));
            jobsTotal = jobQueue.size();
            System.out.println("MASTER NODE:  jobs number is <" + jobsTotal + ">");
                startTime = System.currentTimeMillis();

            try {
                masterProc();
                System.out.println("MASTER IS DONE with results [" + solutionsStep + ": " + solutionsNum + "]");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                System.out.println("MASTER IS KILLED...");
            } catch (InterruptedException e) {}
        } else { // I AM SLAVE
            slaveProc(master);
            System.out.println("SLAVE IS DONE...");
        }
    }

    /**
     * Main procedure of master node. This is basically a job dispatcher.
     */
    private void masterProc() throws IOException, InterruptedException {
        ReceivePort receiver = myIbis.createReceivePort(requestPortType,
                "server", this);

        synchronized(this){
            // Enablings incoming connections have to be in synchronized block, obviously
            receiver.enableConnections();
            receiver.enableMessageUpcalls();
            while(jobQueue.size() > 0)
                this.wait();
        }
    }

    /**
     * Main procedure of slave node. This is basically a job stealer.
     */
    private void slaveProc(IbisIdentifier masterNode) throws IOException, ClassNotFoundException {
        // Sending first JOB_STEALING request
        SendPort sendPort = myIbis.createSendPort(requestPortType);
        sendPort.connect(masterNode, "server");
        ReceivePort receivePort = myIbis.createReceivePort(replyPortType, null);
        receivePort.enableConnections();
        WriteMessage request = sendPort.newMessage();
        MessageObject jobRequest = new MessageObject();
        jobRequest.messageType = MessageObject.message_id.JOB_STEALING;
        jobRequest.requestor = receivePort.identifier();
        request.writeObject(jobRequest);
        request.finish();

        MessageObject localSolutionResult = new MessageObject();
        localSolutionResult.messageType = MessageObject.message_id.JOB_RESULT;
        localSolutionResult.requestor = receivePort.identifier();

        ReadMessage reply = receivePort.receive();
        MessageObject job = (MessageObject)reply.readObject();
        reply.finish();
        while(job.messageType == MessageObject.message_id.JOB_CUBE){ // we (still) have work to do
            if(job.data == null){
                System.err.println("Something went wrong for the slave node [" + myIbis.identifier() + "]; MessageType is " +
                        "JOB_CUBE, but no payload is presented");
                sendPort.close();
                receivePort.close();
                return;
            }

            // Solving current task
            Cube cube = (Cube)job.data;
            minimumBound = job.availSolution;
            Pair<Integer, Integer> res = solve(cube);
            System.out.println("SLAVE NODE  SOLVED ONE "  + res.getKey() + " :: " + res.getValue());
            localSolutionResult.data = res;

            // Sending back the result
            request = sendPort.newMessage();
            request.writeObject(localSolutionResult);
            request.finish();

            // Sending request for a new job
            request = sendPort.newMessage();
            request.writeObject(jobRequest);
            request.finish();

            // Receiving next job(if presented)
            reply = receivePort.receive();
            job = (MessageObject)reply.readObject();
            reply.finish();
        }
    }

    private HashSet<Cube> generateJobs(Cube cube, Integer depth, CubeCache cache){
        if(depth == 0){
            return new HashSet<Cube>(Arrays.asList(cube));
        } else {
            HashSet<Cube> res = new HashSet<Cube>();
            for(Cube cb: cube.generateChildren(cache)){
                res.addAll(generateJobs(cb, depth - 1, cache));
            }
            return res;
        }
    }

    @Override
    public void upcall(ReadMessage message) throws IOException, ClassNotFoundException {
        MessageObject readMessage = (MessageObject) message.readObject();
        message.finish();
            // Notify Master node main thread that all work is done

            ReceivePortIdentifier requestor = readMessage.requestor;
            MessageObject response = new MessageObject();
            response.messageType = MessageObject.message_id.JOB_CUBE;
            if(requestor == null)
                return;
            synchronized (this){
                if(readMessage.messageType == MessageObject.message_id.JOB_STEALING){
                    // Provide slave with one another job
                    response.availSolution = solutionsStep - MAX_HOPS;
                    try{
                        response.data = jobQueue.remove();
                    } catch(Exception e){
                        response.messageType = MessageObject.message_id.EMPTY_MESSAGE;
                    }
                    SendPort replyPort = myIbis.createSendPort(replyPortType);
                    replyPort.connect(requestor);
                    WriteMessage reply = replyPort.newMessage();
                    reply.writeObject((response));
                    reply.finish();
                    replyPort.close();


                } else if (readMessage.messageType == MessageObject.message_id.JOB_RESULT){
                    Pair<Integer, Integer> res = (Pair<Integer, Integer>)readMessage.data;
                    System.out.println("GOT RESULT (" + res.getKey() + " ; " + res.getValue() + ")");
                    --jobsTotal;
                    if(res.getValue() > 0 && (res.getValue() + MAX_HOPS <= solutionsStep)){
                        solutionsStep = res.getValue() + MAX_HOPS;
                        if (res.getValue() == solutionsStep){
                            solutionsNum += res.getKey();
                        } else {
                            solutionsNum = res.getKey();
                        }

//                        SendPort sendPort = myIbis.createSendPort(slaveBroadcastPortType);
//                        IbisIdentifier[] joinedIbises = myIbis.registry().joinedIbises();
//                        for (IbisIdentifier joinedIbis : joinedIbises) {
//                            sendPort.connect(joinedIbis, "receive port");
//                        }
//                        MessageObject mess = new MessageObject();
//                        mess.messageType = MessageObject.message_id.JOB_INFORM;
//                        mess.data = new Long(solutionsStep);
//                        mess.requestor = null; // don't really need it here
//                        try {
//                            WriteMessage toSend = sendPort.newMessage();
//                            toSend.writeObject(mess);
//                            toSend.finish();
//                        } catch (IOException e) {
//                            System.err.println("error when sending message: " + e);
//                        }
//                        sendPort.lostConnections();
                    } else if (res.getValue() == solutionsStep){

                    } else {
                        // do nothing
                    }
                    if(jobQueue.size() == 0 && jobsTotal == 0) {
                        endTime = System.currentTimeMillis();
                        System.out.println("The last job arrived to Master node; Solution Number is <" + solutionsNum + ">; Solutions Step is <" + solutionsStep +
                                ">; Time is <" + (endTime - startTime) + ">" );
                        this.notify();
                    }

                }

            }
    }
}
