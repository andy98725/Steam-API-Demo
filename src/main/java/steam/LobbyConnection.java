package steam;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamMatchmaking;

import util.Utils;

public class LobbyConnection {

	private final LobbyConnectionReciever parent;
	private final SteamConnection steam;

	protected final SteamID lobby;
	private final List<SteamID> members = new ArrayList<SteamID>();

	public LobbyConnection(LobbyConnectionReciever par, SteamConnection steam, SteamID lobbyID) {
		this.parent = par;
		this.steam = steam;
		this.lobby = lobbyID;

		loadMembers();
		parent.connected(this);

		printMembers();
//		update();
	}

	private void loadMembers() {
		int numMembers = steam.mm().getNumLobbyMembers(lobby);
		if (members.size() == numMembers)
			return;

		members.clear();
		for (int i = 0; i < numMembers; i++)
			members.add(steam.mm().getLobbyMemberByIndex(lobby, i));
	}

	private void printMembers() {
		System.out.println("Lobby Size " + members.size());
		for (int i = 0; i < members.size(); i++)
			System.out.println(" - " + i + ": " + steam.username(members.get(i)));

	}

	// TODO test message sending and recieving
	public void sendMessage(Object msg, boolean recieveLocally) {
		try {
			byte[] data = Utils.objToBytes(msg);
			ByteBuffer bb = ByteBuffer.allocateDirect(data.length);
			steam.mm.get().sendLobbyChatMsg(lobby, bb.put(data));

			if (recieveLocally)
				parent.recieveMessage(msg, steam.localID());
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void receieveMessage(int chatID, SteamID sender) {
		try {
			int size = steam.mm.get().getLobbyChatEntry(lobby, chatID, chatEntry, chatRecBuffer);
			byte[] bytes = new byte[size];
			chatRecBuffer.get(bytes);
			parent.recieveMessage(Utils.bytesToObj(bytes), sender);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void userChanged(SteamID changed, SteamID actor) {
		if (!members.contains(changed))
			System.out.println(steam.username(changed) + " joined");
		else if (changed.equals(actor))
			System.out.println(steam.username(changed) + " left");
		else
			System.out.println(steam.username(changed) + " was kicked by " + steam.username(actor));

		loadMembers();
	}

	private final ByteBuffer chatRecBuffer = ByteBuffer.allocateDirect(4096);
	private final SteamMatchmaking.ChatEntry chatEntry = new SteamMatchmaking.ChatEntry();

	public static interface LobbyConnectionReciever {

		public void connected(LobbyConnection parent);

		public void recieveMessage(Object msg, SteamID sender);
	}

}
