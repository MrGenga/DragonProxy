/*
 * GNU LESSER GENERAL PUBLIC LICENSE
 *                       Version 3, 29 June 2007
 *
 * Copyright (C) 2007 Free Software Foundation, Inc. <http://fsf.org/>
 * Everyone is permitted to copy and distribute verbatim copies
 * of this license document, but changing it is not allowed.
 *
 * You can view LICENCE file for details. 
 *
 * @author The Dragonet Team
 */
package org.dragonet.net.packet.minecraft;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.DataFormatException;
import java.util.zip.Inflater;
import org.apache.commons.lang3.ArrayUtils;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.dragonet.net.inf.mcpe.NetworkChannel;
import org.dragonet.proxy.utilities.MCPESkin;
import org.dragonet.proxy.utilities.io.PEBinaryReader;
import org.dragonet.proxy.utilities.io.PEBinaryWriter;

public class LoginPacket extends PEPacket {

    public String username;
    public int protocol;
    public UUID clientUuid;
    public long clientID;
    public String publicKey;
    public String serverAddress;

    public String skinName;
    public MCPESkin skin;

    public LoginPacket(byte[] data) {
        this.setData(data);
    }

    public LoginPacket() {
    }

    @Override
    public int pid() {
        return PEPacketIDs.LOGIN_PACKET;
    }

    @Override
    public void encode() {
        try {
            setChannel(NetworkChannel.CHANNEL_PRIORITY);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            PEBinaryWriter writer = new PEBinaryWriter(bos);
            writer.writeByte((byte) (this.pid() & 0xFF));
            writer.writeInt(protocol);
            
            //TODO
            
            this.setData(bos.toByteArray());
        } catch (IOException e) {
        }
    }

    @Override
    public void decode() {
        try {
            PEBinaryReader reader = new PEBinaryReader(new ByteArrayInputStream(this.getData()));
            reader.readByte(); //PID
            this.protocol = reader.readInt();

            byte[] buff = new byte[40960];
            int len = reader.readInt();
            Inflater inf = new Inflater();
            inf.setInput(reader.read(len));
            int out = inf.inflate(buff);
            inf.end();
            buff = ArrayUtils.subarray(buff, 0, out);
            String strJsonData;
            String strMetaData;
            {
                PEBinaryReader readerPayload = new PEBinaryReader(new ByteArrayInputStream(buff));
                readerPayload.switchEndianness();
                int jsonLen = readerPayload.readInt();
                readerPayload.switchEndianness();
                strJsonData = new String(readerPayload.read(jsonLen), "UTF-8");
                readerPayload.switchEndianness();
                int restLen = readerPayload.readInt();
                readerPayload.switchEndianness();
                strMetaData = new String(readerPayload.read(restLen), "UTF-8");
            }

            // Decode basic info
            {
                JSONObject data = new JSONObject(strJsonData);
                if (data.length() <= 0 || !data.has("chain") || data.optJSONArray("chain") == null) {
                    return;
                }
                String[] chains = decodeJsonStringArray(data.getJSONArray("chain"));
                //System.out.println("Chain count: " + chains.length);
                for (String token : chains) {
                    //System.out.println(" -- processing chain: " + token);
                    JSONObject map = decodeToken(token);
                    
                    if (map == null || map.length() == 0) {
                        continue;
                    }

                    if (map.has("extraData")) {
                        JSONObject extras = map.getJSONObject("extraData");
                        if (extras.has("displayName")) {
                            username = extras.getString("displayName");
                        }
                        if (extras.has("identity")) {
                            this.clientUuid = UUID.fromString(extras.getString("identity"));
                        }
                    }
                    if (map.has("identityPublicKey")) {
                        publicKey = map.getString("identityPublicKey");
                    }
                }
            }
            
            // Decode user metadata
            {
                JSONObject map = decodeToken(strMetaData);
                if(map.has("ClientRandomId")) clientID = map.getLong("ClientRandomId");
                if(map.has("ServerAddress")) serverAddress = map.getString("ServerAddress");
                if(map.has("SkinId")) skinName = map.getString("SkinId");
                if(map.has("SkinData")) skin = new MCPESkin(map.getString("SkinData"), skinName);
            }
        } catch (IOException | DataFormatException | JSONException e) {
            Logger.getLogger(LoginPacket.class.getName()).log(Level.SEVERE, null, e);
        }
    }

    private JSONObject decodeToken(String token) throws JSONException, IOException {
        String[] base = token.split("\\.");
        String strToken = new String(Base64.getDecoder().decode(base[1]), "UTF-8");
        //System.out.println("    decoded: " + strToken);        
        if (base.length < 2) {
            return null;
        }
        return new JSONObject(strToken);
    }

    private String[] decodeJsonStringArray(JSONArray arr) {
        List<String> lst = new ArrayList<>();
        for (int i = 0; i < arr.length(); i++) {
            String s = arr.optString(i);
            if (s != null && !s.isEmpty()) {
                lst.add(s);
            }
        }
        return lst.toArray(new String[0]);
    }

}
