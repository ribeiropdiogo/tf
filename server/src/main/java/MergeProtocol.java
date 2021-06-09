import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class MergeProtocol {

    private final int N_PROCESSES;

    private final List<MergeProtocolProposal> proposals;

    private int nrProcessedProposals;

    public MergeProtocol(int nProcesses){
        this.N_PROCESSES = nProcesses;
        this.nrProcessedProposals = 0;
        this.proposals = new ArrayList<>();
    }

    public MergeProtocol addProposal(MergeProtocolProposal proposal){
        this.proposals.add(proposal);

        // Increment the number of processed proposals
        this.nrProcessedProposals++;

        return this;
    }

    public boolean isComplete(){
        return nrProcessedProposals == N_PROCESSES;
    }

    public MergeProtocolProposal getWinner(){
        if (isComplete()){

            // Sort the array by the merge protocol proposal comparator
            proposals.sort(new MergeProtocolProposalComparator());

            // Return the merge winner
            return proposals.get(0);
        }else {
            return null;
        }
    }

    /**
     * Comparator to compare which of two proposal's has the higher chance of winning
     * the merge.
     */
    private static class MergeProtocolProposalComparator implements Comparator<MergeProtocolProposal> {
        @Override
        public int compare(MergeProtocolProposal proposal_1, MergeProtocolProposal proposal_2) {
            return proposal_1.compareTo(proposal_2);
        }
    }
}
