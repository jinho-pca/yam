package com.ssafy.yam.domain.user.service;

import com.ssafy.yam.domain.user.entity.Authority;
import com.ssafy.yam.domain.bookmark.entity.Bookmark;
import com.ssafy.yam.domain.bookmark.repository.BookmarkRepository;
import com.ssafy.yam.domain.deal.entity.Deal;
import com.ssafy.yam.domain.deal.repository.DealRepository;
import com.ssafy.yam.domain.image.repository.ImageRepository;
import com.ssafy.yam.domain.item.entity.Item;
import com.ssafy.yam.domain.item.repository.ItemRepository;
import com.ssafy.yam.domain.user.dto.request.UserRequestDto;
import com.ssafy.yam.domain.user.dto.response.UserResponseDto;
import com.ssafy.yam.domain.user.entity.User;
import com.ssafy.yam.domain.user.repository.UserRepository;
import com.ssafy.yam.jwt.TokenProvider;
import com.ssafy.yam.utils.S3UploadUtils;
import com.ssafy.yam.utils.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.transaction.Transactional;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;

import static com.ssafy.yam.utils.ConstantsUtils.*;

@RequiredArgsConstructor
@Service
public class UserService {

    private final UserRepository userRepository;
    private final DealRepository dealRepository;
    private final ItemRepository itemRepository;
    private final ImageRepository imageRepository;
    private final BookmarkRepository bookmarkRepository;
    private final PasswordEncoder passwordEncoder;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private final ModelMapper modelMapper;
    private final TokenProvider tokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;

    @Transactional
    public boolean signup(UserRequestDto.SignUp signUp) {
        if(userRepository.existsByUserEmail(signUp.getUserEmail())) {
            return false;
        }

        Authority authority = Authority.builder()
                .authorityName("ROLE_USER")
                .build();

        User user = User.builder()
                .userNickname(signUp.getUserNickname())
                .userEmail(signUp.getUserEmail())
                .userPassword(passwordEncoder.encode(signUp.getUserPassword()))
                .userImageUrl("https://yam-s3.s3.ap-northeast-2.amazonaws.com/profile/defaultImage.png")
                .userAuthLevel(1)
                .authorities(Collections.singleton(authority))
                .build();
        userRepository.save(user);

        return true;
    }

    public UserResponseDto.LoginResDto login(UserRequestDto.Login Login) {
        UsernamePasswordAuthenticationToken authenticationToken =
                new UsernamePasswordAuthenticationToken(Login.getUserEmail(), Login.getUserPassword());

        Authentication authentication = authenticationManagerBuilder.getObject().authenticate(authenticationToken);
        SecurityContextHolder.getContext().setAuthentication(authentication);

        String jwt = tokenProvider.createToken(authentication);

        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(AUTH_HEADER, TOKEN_TYPE + jwt);

        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        String userAddress = userRepository.findByUserEmail(tokenEmail).get().getUserAddress();
        int userAreaCode = userRepository.findByUserEmail(tokenEmail).get().getUserAreaCode();

        return new UserResponseDto.LoginResDto(TOKEN_TYPE + jwt, userAddress, userAreaCode);
    }

    public boolean emailCheck(String userEmail) {
        return userRepository.existsByUserEmail(userEmail);
    }

    public UserResponseDto.SendEmailResDto sendEmail(String userEmail) {
        UserResponseDto.SendEmailResDto sendEmailResDto = new UserResponseDto.SendEmailResDto();

        // ???????????? ??????
        String key = certificationNumberGenerator();
        // ?????? ??????
        UserResponseDto.EmailResDto mail = createEmail(userEmail, key);
        // ?????? ??????
        mailSend(mail);
        sendEmailResDto.setCertificationNumber(key);

        return sendEmailResDto;
    }

    @Autowired
    private JavaMailSender mailSender;
    public void mailSend(UserResponseDto.EmailResDto emailDto) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(emailDto.getEmail());
        message.setFrom(FROM_EMAIL_ADDRESS);
        message.setSubject(emailDto.getTitle());
        message.setText(emailDto.getMessage());
        mailSender.send(message);

    }

    public UserResponseDto.EmailResDto createEmail(String userEmail, String certificationNumber) {
        UserResponseDto.EmailResDto emailResDto = new UserResponseDto.EmailResDto();
        emailResDto.setEmail(userEmail);
        emailResDto.setTitle("YAM ???????????? ?????? ?????? ?????? ?????????.");
        emailResDto.setMessage("???????????????. YAM ???????????? ?????? ?????? ?????? ?????????." + "\n" + "???????????? ??????????????? " + certificationNumber + "?????????.");

        return emailResDto;
    }

    public String certificationNumberGenerator(){

        char[] charSet = new char[] { '0', '1', '2', '3', '4', '5', '6', '7', '8', '9', 'A', 'B', 'C', 'D', 'E', 'F',
                'G', 'H', 'I', 'J', 'K', 'L', 'M', 'N', 'O', 'P', 'Q', 'R', 'S', 'T', 'U', 'V', 'W', 'X', 'Y', 'Z' };

        StringBuilder sb = new StringBuilder();
        int idx = 0;

        for (int i = 0; i < 6; i++) {
            idx = (int) (charSet.length * Math.random());
            sb.append(charSet[idx]);
        }
        return sb.toString();
    }

    @Autowired
    private S3UploadUtils s3UploadUtils;

    @Transactional
    public UserResponseDto.ModifyProfileResDto modifyProfile(MultipartFile userImage, String userNickname) {
        UserResponseDto.ModifyProfileResDto modifyProfileResDto = new UserResponseDto.ModifyProfileResDto(false, false);
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        String userSet = tokenEmail + "(" + LocalDate.now().toString() + ")";
        String imageUrl = null;

        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        if(userImage != null) {
            try {
                imageUrl = s3UploadUtils.upload(userImage, "profile", userSet);
                logger.info(tokenEmail + " : profile image upload s3 success");
            } catch (IOException e){
                logger.info(tokenEmail + " : profile image upload s3 fail");
                e.printStackTrace();
            }
        }

        if(imageUrl != null){
            user.setUserImageUrl(imageUrl);
            modifyProfileResDto.setModifiedImage(true);
        }
        if(userNickname != null){
            user.setUserNickname(userNickname);
            modifyProfileResDto.setModifiedNickname(true);
        }

        userRepository.save(user);

        return modifyProfileResDto;
    }

    public UserResponseDto.ShowProfileResDto showProfile() {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        UserResponseDto.ShowProfileResDto showProfileResDto = modelMapper.map(user, UserResponseDto.ShowProfileResDto.class);

        return showProfileResDto;
    }

    @Transactional
    public boolean modifyAddress(UserRequestDto.ModifyAddress modifyAddress) {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        user.setUserAddress(modifyAddress.getUserAddress());
        user.setUserAreaCode(modifyAddress.getUserAreaCode());
        userRepository.save(user);

        // token ??? ??????????????? ?????? ?????? ?????? ?????? ???????????? ??????.
        return true;
    }

    public UserResponseDto.MonthScheduleResDto getMonthSchedule(String userDate) {
        LocalDate requestDate = LocalDate.parse(userDate);
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        UserResponseDto.MonthScheduleResDto monthScheduleResDto = new UserResponseDto.MonthScheduleResDto();
        int currentMonth = requestDate.getMonthValue();

        List<Deal> giveList = dealRepository.findByBuyer_UserId(user.getUserId()); // ?????? ????????? : ?????? ?????????
        List<Deal> takeList = dealRepository.findBySeller_UserId(user.getUserId()); // ?????? ????????? : ?????? ?????????

        // ?????? ?????? ???????????? ?????? ?????? ????????????, ?????? ?????? ????????? ?????? ????????? ?????? ??????
        List<LocalDate> takeDateList = new ArrayList<>();
        List<LocalDate> giveDateList = new ArrayList<>();
        List<LocalDate> takeEndDateList = new ArrayList<>();
        List<LocalDate> giveEndDateList = new ArrayList<>();
        HashSet<LocalDate> takeDateSet = new HashSet<>();
        HashSet<LocalDate> giveDateSet = new HashSet<>();
        HashSet<LocalDate> takeEndDateSet = new HashSet<>();
        HashSet<LocalDate> giveEndDateSet = new HashSet<>();

        // ?????? ???????????? ?????? : ????????????
        for (int i = 0; i < takeList.size(); i++) {
            if(takeList.get(i).getDealStartDate().getMonthValue() == currentMonth && takeList.get(i).getDealEndDate().getMonthValue() ==currentMonth) {
                // ?????? ???????????? ???????????? ?????? ??? ?????? ????????? ??????
                LocalDate pivotDate = takeList.get(i).getDealStartDate();
                while(pivotDate.isBefore(takeList.get(i).getDealEndDate().plusDays(1))) {
                    takeDateSet.add(pivotDate);
                    pivotDate = pivotDate.plusDays(1);
                }
                // ???????????? ??????
                takeEndDateSet.add(takeList.get(i).getDealEndDate());
            } else if(takeList.get(i).getDealStartDate().getMonthValue() == currentMonth) {
                // ?????? ???????????? ?????? ????????????, ?????? ???????????? ???????????? ?????? ??????
                LocalDate pivotDate = takeList.get(i).getDealStartDate();
                while(pivotDate.getMonthValue() == currentMonth) {
                    takeDateSet.add(pivotDate);
                    pivotDate = pivotDate.plusDays(1);
                }
            } else if(takeList.get(i).getDealEndDate().getMonthValue() == currentMonth) {
                // ?????? ???????????? ?????? ????????????, ?????? ???????????? ???????????? ?????? ??????
                LocalDate pivotDate = takeList.get(i).getDealEndDate();
                while(pivotDate.getMonthValue() == currentMonth){
                    takeDateSet.add(pivotDate);
                    pivotDate = pivotDate.minusDays(1);
                }
                // ???????????? ??????
                takeEndDateSet.add(takeList.get(i).getDealEndDate());
            } else continue;
        }

        for(LocalDate date : takeDateSet) takeDateList.add(date);
        Collections.sort(takeDateList);
        monthScheduleResDto.set????????????(takeDateList);

        for(LocalDate date : takeEndDateSet) takeEndDateList.add(date);
        Collections.sort(takeEndDateList);
        monthScheduleResDto.set????????????(takeEndDateList);

        // ?????? ???????????? ?????? : ????????????
        for (int i = 0; i < giveList.size(); i++) {
            if(giveList.get(i).getDealStartDate().getMonthValue() == currentMonth && giveList.get(i).getDealEndDate().getMonthValue() ==currentMonth) {
                // ?????? ???????????? ???????????? ?????? ??? ?????? ????????? ??????
                LocalDate pivotDate = giveList.get(i).getDealStartDate();
                while(pivotDate.isBefore(giveList.get(i).getDealEndDate().plusDays(1))) {
                    giveDateSet.add(pivotDate);
                    pivotDate = pivotDate.plusDays(1);
                }
                // ???????????? ??????
                giveEndDateSet.add(giveList.get(i).getDealEndDate());
            } else if(giveList.get(i).getDealStartDate().getMonthValue() == currentMonth) {
                // ?????? ???????????? ?????? ????????????, ?????? ???????????? ???????????? ?????? ??????
                LocalDate pivotDate = giveList.get(i).getDealStartDate();
                while(pivotDate.getMonthValue() == currentMonth) {
                    giveDateSet.add(pivotDate);
                    pivotDate = pivotDate.plusDays(1);
                }
            } else if(giveList.get(i).getDealEndDate().getMonthValue() == currentMonth) {
                // ?????? ???????????? ?????? ????????????, ?????? ???????????? ???????????? ?????? ??????
                LocalDate pivotDate = giveList.get(i).getDealEndDate();
                while(pivotDate.getMonthValue() == currentMonth){
                    giveDateSet.add(pivotDate);
                    pivotDate = pivotDate.minusDays(1);
                }
                // ???????????? ??????
                giveEndDateSet.add(giveList.get(i).getDealEndDate());
            } else continue;
        }

        for(LocalDate date : giveDateSet) giveDateList.add(date);
        Collections.sort(giveDateList);
        monthScheduleResDto.set????????????(giveDateList);

        for(LocalDate date : giveEndDateSet) giveEndDateList.add(date);
        Collections.sort(giveEndDateList);
        monthScheduleResDto.set????????????(giveEndDateList);

        return monthScheduleResDto;
    }

    public UserResponseDto.DayScheduleResDto getDaySchedule(String userDate) {
        LocalDate requestDate = LocalDate.parse(userDate);
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        UserResponseDto.DayScheduleResDto dayScheduleResDto = new UserResponseDto.DayScheduleResDto();

        List<Deal> dealList = dealRepository.findByBuyer_UserIdOrSeller_UserId(user.getUserId(), user.getUserId());
        List<UserResponseDto.GiveResDto> giveList = new ArrayList<>();
        List<UserResponseDto.TakeResDto> takeList = new ArrayList<>();

        for (int i = 0; i < dealList.size(); i++) {
            // ???????????? ????????? ????????? ????????? ????????? ???????????? ????????? ????????? ?????? ??????
            if(requestDate.isAfter(dealList.get(i).getDealStartDate().minusDays(1)) && requestDate.isBefore(dealList.get(i).getDealEndDate().plusDays(1))) {
                if(dealList.get(i).getSeller().getUserId() == user.getUserId()) {
                    // ?????? ???????????? ????????? == take
                    UserResponseDto.TakeResDto tmpTake = new UserResponseDto.TakeResDto();
                    Item tmpItem = itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId());
                    tmpTake.setItemId(tmpItem.getItemId());
                    tmpTake.setItemName(tmpItem.getItemName());
                    tmpTake.setItemBuyerNickname(userRepository.findByUserId(dealList.get(i).getBuyer().getUserId()).get().getUserNickname());
                    tmpTake.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmpItem.getItemId()));
                    tmpTake.setDealId(dealList.get(i).getDealId());
                    tmpTake.setDealStartDate(dealList.get(i).getDealStartDate());
                    tmpTake.setDealEndDate(dealList.get(i).getDealEndDate());
                    takeList.add(tmpTake);
                } else {
                    // ?????? ???????????? ?????? ????????? == give
                    UserResponseDto.GiveResDto tmpGive = new UserResponseDto.GiveResDto();
                    Item tmpItem = itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId());
                    tmpGive.setItemId(tmpItem.getItemId());
                    tmpGive.setItemName(tmpItem.getItemName());
                    tmpGive.setItemSellerNickname(userRepository.findByUserId(dealList.get(i).getSeller().getUserId()).get().getUserNickname());
                    tmpGive.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmpItem.getItemId()));
                    tmpGive.setDealStartDate(dealList.get(i).getDealStartDate());
                    tmpGive.setDealEndDate(dealList.get(i).getDealEndDate());
                    giveList.add(tmpGive);
                }

            } else continue;
        }

        dayScheduleResDto.set????????????(giveList);
        dayScheduleResDto.set????????????(takeList);

        return dayScheduleResDto;
    }

    public List<UserResponseDto.GetGiveItemResDto> getGiveItem() {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.GetGiveItemResDto> giveItemList = new ArrayList<>();
        List<Item> itemList = itemRepository.findAllBySeller_UserIdOrderByItemModifiedTimeDesc(user.getUserId());
        for (int i = 0; i < itemList.size(); i++) {
            UserResponseDto.GetGiveItemResDto tmp = modelMapper.map(itemList.get(i), UserResponseDto.GetGiveItemResDto.class);
            tmp.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmp.getItemId()));
            giveItemList.add(tmp);
        }

        return giveItemList;
    }

    public String switchItemActive(int itemId) {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        Item item = itemRepository.findItemByItemId(itemId);
        String status = item.getItemIsActive();
        status = status.equals("Y") ? "N" : "Y";
        String result = status.equals("Y") ? "?????????" : "????????????";
        item.setItemIsActive(status);
        itemRepository.save(item);

        return String.format("%d??? ???????????? %s ???????????????.", itemId, result);
    }

    public List<UserResponseDto.GetTakeItemResDto> getTakeItem() {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.GetTakeItemResDto> takeItemList = new ArrayList<>();
        List<Deal> dealList = dealRepository.findByBuyer_UserIdOrderByDealStartDateDesc(user.getUserId());
        for (int i = 0; i < dealList.size(); i++) {
            UserResponseDto.GetTakeItemResDto tmp = modelMapper.map(dealList.get(i), UserResponseDto.GetTakeItemResDto.class);
            tmp.setItemId(dealList.get(i).getItem().getItemId());
            tmp.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(dealList.get(i).getItem().getItemId()));
            tmp.setItemAddress(itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId()).getItemAddress());
            tmp.setItemName(itemRepository.findItemByItemId(dealList.get(i).getItem().getItemId()).getItemName());
            takeItemList.add(tmp);
        }

        return takeItemList;
    }

    public List<UserResponseDto.GetItemHistoryResDto> getItemHistory(int itemid) {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.GetItemHistoryResDto> historyList = new ArrayList<>();
        List<Deal> dealList = dealRepository.findAllByItem_ItemIdOrderByDealStartDateDesc(itemid);
        for (int i = 0; i < dealList.size(); i++) {
            UserResponseDto.GetItemHistoryResDto tmp = modelMapper.map(dealList.get(i), UserResponseDto.GetItemHistoryResDto.class);
            tmp.setItemBuyerImage(userRepository.findByUserId(dealList.get(i).getBuyer().getUserId()).get().getUserImageUrl());
            tmp.setItemBuyerNickname(userRepository.findByUserId(dealList.get(i).getBuyer().getUserId()).get().getUserNickname());
            historyList.add(tmp);
        }

        return historyList;
    }

    public UserResponseDto.Receipt getReceipt(int dealId) {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        Deal deal = dealRepository.findByDealId(dealId).get();
        UserResponseDto.Receipt receipt = modelMapper.map(deal, UserResponseDto.Receipt.class);
        receipt.setItemId(deal.getItem().getItemId());
        receipt.setItemName(deal.getItem().getItemName());
        receipt.setItemBuyerNickname(deal.getBuyer().getUserNickname());
        receipt.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(deal.getItem().getItemId()));
        receipt.setItemAddress(deal.getItem().getItemAddress());
        receipt.setItemPrice(deal.getItem().getItemPrice());

        return receipt;
    }

    public List<UserResponseDto.WishList> getWishList() {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        List<UserResponseDto.WishList> wishList = new ArrayList<>();
        List<Bookmark> bookmarkList = bookmarkRepository.findAllByBookmarkId_UserId(user.getUserId());
        for (int i = 0; i < bookmarkList.size(); i++) {
            Item item = itemRepository.findItemByItemId(bookmarkList.get(i).getBookmarkId().getItemId());
            UserResponseDto.WishList tmp = modelMapper.map(item, UserResponseDto.WishList.class);
            tmp.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(tmp.getItemId()));
            wishList.add(tmp);
        }

        return wishList;
    }

    public UserResponseDto.MeResDto getMe() {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));

        UserResponseDto.MeResDto meResDto = modelMapper.map(user, UserResponseDto.MeResDto.class);

        return meResDto;
    }

    public List<UserResponseDto.ChatInfoResDto> getChatInfo(List<UserRequestDto.ChatInfoReqDto> mapList) {
        String tokenEmail = SecurityUtils.getCurrentUsername().get();
        User user = userRepository.findByUserEmail(tokenEmail)
                .orElseThrow(() -> new IllegalArgumentException(("?????? ????????? ????????????.")));

        List<UserResponseDto.ChatInfoResDto> chatInfoResDtoList = new ArrayList<>();
        for (int i = 0; i < mapList.size(); i++) {
            System.out.println((i+1)+"?????? mapList : " + mapList.get(i).toString());
            UserResponseDto.ChatInfoResDto tmp = modelMapper.map(itemRepository.findItemByItemId(mapList.get(i).getItemId()), UserResponseDto.ChatInfoResDto.class);
            tmp.setItemImage(imageRepository.findAllImageUrlByItem_ItemId(mapList.get(i).getItemId()));
            tmp.setItemSellerNickname(userRepository.findByUserId(tmp.getItemSellerId()).get().getUserNickname());
            tmp.setItemSellerImageUrl(userRepository.findByUserId(tmp.getItemSellerId()).get().getUserImageUrl());
            tmp.setUserId(mapList.get(i).getUserId());
            tmp.setUserNickname(userRepository.findByUserId(tmp.getUserId()).get().getUserNickname());
            tmp.setUserImageUrl(userRepository.findByUserId(tmp.getUserId()).get().getUserImageUrl());

            chatInfoResDtoList.add(tmp);
        }

        return chatInfoResDtoList;
    }
}
