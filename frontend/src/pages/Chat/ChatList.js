import React, { useEffect, useState } from "react";
import ChatDetail from "./ChatDetail";

import { Link } from "react-router-dom";
import { useSelector, useDispatch } from "react-redux";
import ChatRoom from "../ChatRoom/ChatRoom";

function ChatList(props) {
  // 더미 데이터
  const conversationList = useSelector((state) => state.conversationlist);
  const [chatOpen, setChatOpen] = useState();

  return (
    <div>
      {chatOpen ? <ChatRoom client={props.client} to={chatOpen} setChatOpen={setChatOpen} /> : null}
      {Object.keys(conversationList)
        .sort(function (a, b) {
          return (
            new Date(
              conversationList[b].list[conversationList[b].list.length - 1].timestamp
            ).getTime() -
            new Date(
              conversationList[a].list[conversationList[a].list.length - 1].timestamp
            ).getTime()
          );
        })
        .map((key) => (
          <>
            <ChatDetail
              key={key}
              client={props.client}
              conversation={conversationList[key]}
              user={key}
              setChatOpen={setChatOpen}
            />
          </>
        ))}
    </div>
  );
}

export default ChatList;
