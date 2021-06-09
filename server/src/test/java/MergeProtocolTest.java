public class MergeProtocolTest {

    public static void main(String[] args){

        MergeProtocol mergeProtocol = new MergeProtocol(4);

        MergeProtocolProposal proposal1 = new MergeProtocolProposal("server1", 5, 2);
        MergeProtocolProposal proposal2 = new MergeProtocolProposal("server2", 5, 0);
        MergeProtocolProposal proposal3 = new MergeProtocolProposal("server3", 5, 2);
        MergeProtocolProposal proposal4 = new MergeProtocolProposal("server4", 5, 3);

        mergeProtocol.addProposal(proposal1)
                     .addProposal(proposal2)
                     .addProposal(proposal3)
                     .addProposal(proposal4);

        MergeProtocolProposal winner = mergeProtocol.getWinner();

        System.out.println("\n" + Colors.ANSI_GREEN + " > The Winner is: " + winner.getPROCESS_ID() + Colors.ANSI_RESET);
    }
}
