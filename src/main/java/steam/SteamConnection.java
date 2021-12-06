package steam;

import java.util.Scanner;

import com.codedisaster.steamworks.SteamAPI;
import com.codedisaster.steamworks.SteamAuth.AuthSessionResponse;
import com.codedisaster.steamworks.SteamException;
import com.codedisaster.steamworks.SteamFriends;
import com.codedisaster.steamworks.SteamFriends.PersonaChange;
import com.codedisaster.steamworks.SteamFriendsCallback;
import com.codedisaster.steamworks.SteamID;
import com.codedisaster.steamworks.SteamMatchmaking;
import com.codedisaster.steamworks.SteamMatchmaking.ChatEntryType;
import com.codedisaster.steamworks.SteamMatchmaking.ChatMemberStateChange;
import com.codedisaster.steamworks.SteamMatchmaking.ChatRoomEnterResponse;
import com.codedisaster.steamworks.SteamMatchmaking.LobbyType;
import com.codedisaster.steamworks.SteamMatchmakingCallback;
import com.codedisaster.steamworks.SteamNetworking;
import com.codedisaster.steamworks.SteamNetworking.P2PSessionError;
import com.codedisaster.steamworks.SteamNetworkingCallback;
import com.codedisaster.steamworks.SteamResult;
import com.codedisaster.steamworks.SteamUser;
import com.codedisaster.steamworks.SteamUserCallback;

import steam.LobbyConnection.LobbyConnectionReciever;
import util.LazyLoad;

public class SteamConnection {

	public static void main(String[] args) throws Exception {
		SteamConnection conn = new SteamConnection();
		conn.connectUnranked(new LobbyConnectionReciever() {
			@Override
			public void connected(LobbyConnection lobby) {
				System.out.println("Connected to lobby.");
			}

			@Override
			public void recieveMessage(Object msg, SteamID sender) {
				System.out.println(conn.username(sender) + ": " + msg);
			}

		});

		new Thread(new Runnable() {
			@Override
			public void run() {
				try {
					while (true) {
						Thread.sleep((long) (1e3 / 30));
						conn.logic();
					}
				} catch (InterruptedException e) {
				}
			}
		}, "Steam Loop").start();

		try (Scanner inp = new Scanner(System.in)) {
			while (true) {
				String msg = inp.nextLine();
				if (conn.curLobby != null)
					conn.curLobby.sendMessage(msg, true);
			}
		}
	}

	public SteamConnection() throws SteamException {
		SteamAPI.loadLibraries();
		if (!SteamAPI.init())
			throw new SteamException("Steamworks initialization failed. Is Steam running?");
	}

	private final SteamConnection root = this;

	public void connectUnranked(LobbyConnection.LobbyConnectionReciever par) {
		if (inLobby())
			return;

		MatchmakingReciever joiningLobby = new MatchmakingReciever() {
			@Override
			public void onLobbyEnter(SteamID steamIDLobby, int chatPermissions, boolean blocked,
					ChatRoomEnterResponse response) {
				if (response != ChatRoomEnterResponse.Success)
					throw new RuntimeException("onLobbyEnter failed: " + response);

				curLobby = new LobbyConnection(par, root, steamIDLobby);
			}

			@Override
			public void onLobbyCreated(SteamResult result, SteamID steamIDLobby) {
				if (result != SteamResult.OK)
					throw new RuntimeException("onLobbyCreated failed: " + result);

				mm.get().setLobbyData(steamIDLobby, "Mode", "Unranked");

				curLobby = new LobbyConnection(par, root, steamIDLobby);
			}

		};
		MatchmakingReciever recievedLobbyList = new MatchmakingReciever() {
			@Override
			public void onLobbyMatchList(int lobbiesMatching) {
				SteamID lobby = mm.get().getLobbyByIndex(0);

				mmResponse = joiningLobby;
				if (lobby.isValid())
					mm.get().joinLobby(lobby);
				else
					mm.get().createLobby(LobbyType.Public, 2);

			}
		};

		mmResponse = recievedLobbyList;
		mm.get().addRequestLobbyListStringFilter("Mode", "Unranked", SteamMatchmaking.LobbyComparison.Equal);
		mm.get().requestLobbyList();
	}

	public void logic() {
		if (SteamAPI.isSteamRunning())
			SteamAPI.runCallbacks();
	}

	public void exit() {
		if (mm.has())
			mm.get().dispose();

		SteamAPI.shutdown();
	}

	private LobbyConnection curLobby;

	public boolean inLobby() {
		return curLobby != null;
	}

	public boolean inLobby(SteamID lobbyID) {
		return curLobby != null && curLobby.lobby.equals(lobbyID);
	}

	public LobbyConnection lobby() {
		return curLobby;
	}

	private MatchmakingReciever mmResponse;

	private MatchmakingReciever clearMMResponse() {
		if (mmResponse == null)
			throw new RuntimeException("Reciever method called before response object set");

		MatchmakingReciever ret = mmResponse;
		mmResponse = null;
		return ret;
	}

	public SteamMatchmaking mm() {
		return mm.get();
	}

	@SuppressWarnings("serial")
	protected final LazyLoad<SteamMatchmaking> mm = new LazyLoad<SteamMatchmaking>() {
		@Override
		public SteamMatchmaking make() {
			return new SteamMatchmaking(new SteamMatchmakingCallback() {
				@Override
				public void onFavoritesListChanged(int ip, int queryPort, int connPort, int appID, int flags,
						boolean add, int accountID) {
					System.err.println("onFavoritesListChanged");
				}

				@Override
				public void onLobbyInvite(SteamID steamIDUser, SteamID steamIDLobby, long gameID) {
					System.err.println("onLobbyInvite " + username(steamIDUser) + ", " + steamIDLobby);
				}

				@Override
				public void onLobbyEnter(SteamID steamIDLobby, int chatPermissions, boolean blocked,
						ChatRoomEnterResponse response) {
					clearMMResponse().onLobbyEnter(steamIDLobby, chatPermissions, blocked, response);
				}

				@Override
				public void onLobbyDataUpdate(SteamID steamIDLobby, SteamID steamIDMember, boolean success) {
				}

				@Override
				public void onLobbyChatUpdate(SteamID steamIDLobby, SteamID steamIDUserChanged,
						SteamID steamIDMakingChange, ChatMemberStateChange stateChange) {
					if (inLobby(steamIDLobby))
						curLobby.userChanged(steamIDUserChanged, steamIDMakingChange);
				}

				@Override
				public void onLobbyChatMessage(SteamID steamIDLobby, SteamID steamIDUser, ChatEntryType entryType,
						int chatID) {
					System.err.println("onLobbyChatMessage: " + inLobby(steamIDLobby));
					if (inLobby(steamIDLobby))
						curLobby.receieveMessage(chatID, steamIDUser);
				}

				@Override
				public void onLobbyGameCreated(SteamID steamIDLobby, SteamID steamIDGameServer, int ip, short port) {
					clearMMResponse().onLobbyGameCreated(steamIDLobby, steamIDGameServer, ip, port);
				}

				@Override
				public void onLobbyMatchList(int lobbiesMatching) {
					clearMMResponse().onLobbyMatchList(lobbiesMatching);
				}

				@Override
				public void onLobbyKicked(SteamID steamIDLobby, SteamID steamIDAdmin, boolean kickedDueToDisconnect) {
					System.err.println("onLobbyKicked");
				}

				@Override
				public void onLobbyCreated(SteamResult result, SteamID steamIDLobby) {
					clearMMResponse().onLobbyCreated(result, steamIDLobby);
				}

				@Override
				public void onFavoritesListAccountsUpdated(SteamResult result) {
					System.err.println("onFavoritesListAccountsUpdated");
				}
			});
		}
	};

	private static abstract class MatchmakingReciever implements SteamMatchmakingCallback {

		// "Default methods"
		// implement when needed in child classes
		@Override
		public void onFavoritesListChanged(int ip, int queryPort, int connPort, int appID, int flags, boolean add,
				int accountID) {
			System.err.println("onFavoritesListChanged");
		}

		@Override
		public void onLobbyInvite(SteamID steamIDUser, SteamID steamIDLobby, long gameID) {
			System.err.println("onLobbyInvite");
		}

		@Override
		public void onLobbyEnter(SteamID steamIDLobby, int chatPermissions, boolean blocked,
				ChatRoomEnterResponse response) {
			System.err.println("onLobbyEnter");
		}

		@Override
		public void onLobbyDataUpdate(SteamID steamIDLobby, SteamID steamIDMember, boolean success) {
			System.err.println("onLobbyDataUpdate");
		}

		@Override
		public void onLobbyChatUpdate(SteamID steamIDLobby, SteamID steamIDUserChanged, SteamID steamIDMakingChange,
				ChatMemberStateChange stateChange) {
			System.err.println("onLobbyChatUpdate");
		}

		@Override
		public void onLobbyChatMessage(SteamID steamIDLobby, SteamID steamIDUser, ChatEntryType entryType, int chatID) {
			System.err.println("onLobbyChatMessage");
		}

		@Override
		public void onLobbyGameCreated(SteamID steamIDLobby, SteamID steamIDGameServer, int ip, short port) {
			System.err.println("onLobbyGameCreated");
		}

		@Override
		public void onLobbyMatchList(int lobbiesMatching) {
			System.err.println("onLobbyMatchList");
		}

		@Override
		public void onLobbyKicked(SteamID steamIDLobby, SteamID steamIDAdmin, boolean kickedDueToDisconnect) {
			System.err.println("onLobbyKicked");
		}

		@Override
		public void onLobbyCreated(SteamResult result, SteamID steamIDLobby) {
			System.err.println("onLobbyCreated");
		}

		@Override
		public void onFavoritesListAccountsUpdated(SteamResult result) {
			System.err.println("onFavoritesListAccountsUpdated");
		}
	}

	@SuppressWarnings("serial")
	protected final LazyLoad<SteamUser> user = new LazyLoad<SteamUser>() {
		@Override
		public SteamUser make() {
			return new SteamUser(new SteamUserCallback() {
				@Override
				public void onValidateAuthTicket(SteamID steamID, AuthSessionResponse authSessionResponse,
						SteamID ownerSteamID) {
				}

				@Override
				public void onMicroTxnAuthorization(int appID, long orderID, boolean authorized) {
				}

				@Override
				public void onEncryptedAppTicket(SteamResult result) {
				}
			});
		}
	};

	@SuppressWarnings("serial")
	protected final LazyLoad<SteamNetworking> net = new LazyLoad<SteamNetworking>() {
		@Override
		public SteamNetworking make() {
			return new SteamNetworking(new SteamNetworkingCallback() {
				@Override
				public void onP2PSessionConnectFail(SteamID steamIDRemote, P2PSessionError sessionError) {
					System.err.println("onP2PSessionConnectFail " + steamIDRemote + " " + sessionError);
				}

				@Override
				public void onP2PSessionRequest(SteamID steamIDRemote) {
					System.err.println("onP2PSessionRequest " + steamIDRemote);
				}
			});
		}
	};
	@SuppressWarnings("serial")
	protected final LazyLoad<SteamFriends> friends = new LazyLoad<SteamFriends>() {
		@Override
		public SteamFriends make() {
			return new SteamFriends(new SteamFriendsCallback() {
				@Override
				public void onSetPersonaNameResponse(boolean success, boolean localSuccess, SteamResult result) {
				}

				@Override
				public void onPersonaStateChange(SteamID steamID, PersonaChange change) {
				}

				@Override
				public void onGameOverlayActivated(boolean active) {
				}

				@Override
				public void onGameLobbyJoinRequested(SteamID steamIDLobby, SteamID steamIDFriend) {
				}

				@Override
				public void onAvatarImageLoaded(SteamID steamID, int image, int width, int height) {

				}

				@Override
				public void onFriendRichPresenceUpdate(SteamID steamIDFriend, int appID) {

				}

				@Override
				public void onGameRichPresenceJoinRequested(SteamID steamIDFriend, String connect) {

				}

				@Override
				public void onGameServerChangeRequested(String server, String password) {
				}
			});
		}
	};

	public SteamID localID() {
		return user.get().getSteamID();
	}

	public String username(SteamID id) {
		return friends.get().getFriendPersonaName(id);
	}
}
