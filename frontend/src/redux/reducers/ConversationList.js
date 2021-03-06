const NEWPARTNER = "conversationlist/NEWPARTNER";
const NEWMESSAGE = "conversationlist/NEWMESSAGE";
const RECEIVED = "conversationlist/RECEIVED";
const READ = "conversationlist/READ";

export const insertPartner = (partner) => ({ type: NEWPARTNER, payload: partner });
export const insertMessage = (msg) => ({ type: NEWMESSAGE, payload: msg });
export const receive = (msg) => ({ type: RECEIVED, payload: msg });
export const read = (partner) => ({ type: READ, payload: partner });

const initialState = [];

const ConversationList = (state = initialState, action) => {
  switch (action.type) {
    case NEWPARTNER:
      state[action.payload.partner] = {
        name: action.payload.partnerNickname,
        itemPk: action.payload.itemPk,
        userImg: action.payload.partnerImg,
        itemImg: action.payload.itemImg,
        itemName: action.payload.itemName,
        lastMsg:
          action.payload.lastMsg.type === "message" ? action.payload.lastMsg.message : "거래 요청",
        timestamp: action.payload.lastMsg.timestamp,
        list: [...action.payload.list],
        newmsg: false,
      };
      return { ...state };
    case NEWMESSAGE:
      if (action.payload.type === "create") {
        const tmp = JSON.parse(action.payload.message);
        state[action.payload.to] = {
          name: tmp.name,
          itemPk: action.payload.itemPk,
          userImg: tmp.userImg,
          itemImg: tmp.itemImg,
          itemName: tmp.itemName,
          lastMsg: null,
          list: [],
          newmsg: false,
        };
      }
      state[action.payload.to].list = [...state[action.payload.to].list, action.payload];
      state[action.payload.to].lastMsg =
        action.payload.type === "message" ? action.payload.message : "거래요청";
      state[action.payload.to].timestamp = action.payload.timestamp;
      return { ...state };
    case RECEIVED:
      if (action.payload.type === "create") {
        const tmp = JSON.parse(action.payload.message);
        state[action.payload.author] = {
          name: tmp.author.name,
          itemPk: action.payload.itemPk,
          userImg: tmp.author.userImg,
          itemImg: tmp.itemImg,
          itemName: tmp.itemName,
          lastMsg: null,
          list: [],
          newmsg: true,
        };
      }

      if (state[action.payload.author] === undefined) {
        state[action.payload.author].list = [action.payload];
      } else {
        state[action.payload.author].list = [...state[action.payload.author].list, action.payload];
      }
      state[action.payload.author].timestamp = action.payload.timestamp;
      state[action.payload.author].newmsg = true;
      state[action.payload.author].lastMsg =
        action.payload.type === "message" ? action.payload.message : "거래요청";

      return { ...state };
    case READ:
      state[action.payload.room].newmsg = false;
      return { ...state };
    default:
      return state;
  }
};

export default ConversationList;
