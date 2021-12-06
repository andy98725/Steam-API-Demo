# Steam API Demo

## Overview

SteamConnection contains main.
SteamConnection manages Steam initialization and all Steam Interfaces.

When the user connects to a lobby, a LobbyConnection is instaniated to handle communication.

## Issue
When connected to a lobby with more than 1 user, and SteamMatchmaking.sendLobbyChatMsg is called (done in LobbyConnection.sendMessage),
the other users do not recieve the message.

## Testing

Run SteamConnection using 2 separate Steam accounts and the same steam_appid.
Each user entering/leaving lobby should display correctly, but any messages (read from stdin) do not transmit to the other user properly.