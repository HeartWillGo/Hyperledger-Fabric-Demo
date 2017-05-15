package com.cs.fabric.client;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Set;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.hyperledger.fabric.sdk.Chain;
import org.hyperledger.fabric.sdk.ChainCodeID;
import org.hyperledger.fabric.sdk.ChaincodeEndorsementPolicy;
import org.hyperledger.fabric.sdk.HFClient;
import org.hyperledger.fabric.sdk.InstallProposalRequest;
import org.hyperledger.fabric.sdk.InstantiateProposalRequest;
import org.hyperledger.fabric.sdk.Peer;
import org.hyperledger.fabric.sdk.ProposalResponse;

import com.cs.fabric.client.utils.ClientHelper;
import com.cs.fabric.sdkintegration.SampleOrg;

public class DeployChainCode {

	private static final ClientHelper clientHelper = new ClientHelper();
	private static final String FOO_CHAIN_NAME = "foo";
	private static final String TEST_FIXTURES_PATH = "src/test/fixture";
	private static final String CHAIN_CODE_NAME = "trade_finance_go";
	private static final String CHAIN_CODE_PATH = "github.com/trade_finance";
	private static final String CHAIN_CODE_VERSION = "1";

	private static final Log logger = LogFactory.getLog(DeployChainCode.class);

	public static void main(String[] args) throws Exception {

		// Get Org1
		SampleOrg sampleOrg = clientHelper.getSamleOrg();

		// Create instance of client.
		HFClient client = clientHelper.getHFClient();

		client.setUserContext(sampleOrg.getPeerAdmin());

		Chain chain = clientHelper.getChainWithPeerAdmin();
		logger.info("Get Chain " + FOO_CHAIN_NAME);

		final ChainCodeID chainCodeID;
		Collection<ProposalResponse> responses;
		Collection<ProposalResponse> successful = new LinkedList<>();
		Collection<ProposalResponse> failed = new LinkedList<>();

		chainCodeID = ChainCodeID.newBuilder().setName(CHAIN_CODE_NAME).setVersion(CHAIN_CODE_VERSION)
				.setPath(CHAIN_CODE_PATH).build();
		logger.info("Chain Code Name:" + chainCodeID.getName() + "; Version:" + chainCodeID.getVersion() + "; Path:"
				+ chainCodeID.getPath());

		////////////////////////////
		// Install Proposal Request
		//
		System.out.println("Creating install proposal");

		InstallProposalRequest installProposalRequest = client.newInstallProposalRequest();
		installProposalRequest.setChaincodeID(chainCodeID);
		//// For GO language and serving just a single user, chaincodeSource is
		//// mostly likely the users GOPATH
		installProposalRequest
				.setChaincodeSourceLocation(new File(TEST_FIXTURES_PATH + "/sdkintegration/gocc/sample2"));
		installProposalRequest.setChaincodeVersion(CHAIN_CODE_VERSION);

		System.out.println("Sending install proposal");
		////////////////////////////
		// only a client from the same org as the peer can issue an install
		//////////////////////////// request
		int numInstallProposal = 0;
		// Set<String> orgs = orgPeers.keySet();
		// for (SampleOrg org : testSampleOrgs) {

		Set<Peer> peersFromOrg = sampleOrg.getPeers();
		numInstallProposal = numInstallProposal + peersFromOrg.size();
		responses = client.sendInstallProposal(installProposalRequest, chain.getPeers());

		for (ProposalResponse response : responses) {
			if (response.getStatus() == ProposalResponse.Status.SUCCESS) {
				System.out.println("Successful install proposal response Txid: " + response.getTransactionID()
						+ " from peer" + response.getPeer().getName());
				successful.add(response);
			} else {
				failed.add(response);
			}
		}

		System.out.println("Received " + numInstallProposal + " install proposal responses. Successful+verified: "
				+ successful.size() + ". Failed: " + failed.size());

		if (failed.size() > 0) {
			ProposalResponse first = failed.iterator().next();
			System.out.println("Not enough endorsers for install :" + successful.size() + ".  " + first.getMessage());
		}

		///////////////
		//// Instantiate chain code.
		InstantiateProposalRequest instantiateProposalRequest = client.newInstantiationProposalRequest();
		instantiateProposalRequest.setProposalWaitTime(60000);
		instantiateProposalRequest.setChaincodeID(chainCodeID);
		instantiateProposalRequest.setFcn("init");
		instantiateProposalRequest.setArgs(new String[] {});
		Map<String, byte[]> tm = new HashMap<>();
		tm.put("HyperLedgerFabric", "InstantiateProposalRequest:JavaSDK".getBytes(UTF_8));
		tm.put("method", "InstantiateProposalRequest".getBytes(UTF_8));
		instantiateProposalRequest.setTransientMap(tm);

		/*
		 * policy OR(Org1MSP.member, Org2MSP.member) meaning 1 signature from
		 * someone in either Org1 or Org2 See README.md Chaincode endorsement
		 * policies section for more details.
		 */
		ChaincodeEndorsementPolicy chaincodeEndorsementPolicy = new ChaincodeEndorsementPolicy();
		chaincodeEndorsementPolicy
				.fromYamlFile(new File(TEST_FIXTURES_PATH + "/sdkintegration/chaincodeendorsementpolicy.yaml"));
		instantiateProposalRequest.setChaincodeEndorsementPolicy(chaincodeEndorsementPolicy);

		System.out.println("Sending instantiateProposalRequest to all peers without arguments");
		successful.clear();
		failed.clear();

		// client.setUserContext(sampleOrg.getAdmin());
		responses = chain.sendInstantiationProposal(instantiateProposalRequest, chain.getPeers());
		for (ProposalResponse response : responses) {
			if (response.isVerified() && response.getStatus() == ProposalResponse.Status.SUCCESS) {
				successful.add(response);
				System.out.println("Succesful instantiate proposal response Txid: " + response.getTransactionID()
						+ " from peer " + response.getPeer().getName());
			} else {
				failed.add(response);
			}
		}
		System.out.println("Received " + responses.size() + " instantiate proposal responses. Successful+verified: "
				+ successful.size() + ". Failed: " + failed.size());
		if (failed.size() > 0) {
			ProposalResponse first = failed.iterator().next();
			System.out.println("Not enough endorsers for instantiate :" + successful.size() + "endorser failed with "
					+ first.getMessage() + ". Was verified:" + first.isVerified());
		}

		///////////////
		/// Send instantiate transaction to orderer
		System.out.println("Sending instantiateTransaction to orderer without arguments");
		chain.sendTransaction(successful, chain.getOrderers()).thenApply(transactionEvent -> {
			
			if (transactionEvent.isValid()) {
				System.out.println("Finished transaction with transaction id " + transactionEvent.getTransactionID());
			}
			return null;
		});
	}
}