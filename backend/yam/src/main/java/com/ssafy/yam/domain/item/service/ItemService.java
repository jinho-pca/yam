package com.ssafy.yam.domain.item.service;

import com.ssafy.yam.domain.bookmark.repository.BookmarkRepository;
import com.ssafy.yam.domain.deal.dto.response.DealResponse;
import com.ssafy.yam.domain.deal.repository.DealRepository;
import com.ssafy.yam.domain.deal.service.DealService;
import com.ssafy.yam.domain.image.entity.Image;
import com.ssafy.yam.domain.image.repository.ImageRepository;
import com.ssafy.yam.domain.item.dto.request.ItemCreateRequest;
import com.ssafy.yam.domain.item.dto.response.ItemDetailResponse;
import com.ssafy.yam.domain.item.dto.response.ItemListResponse;
import com.ssafy.yam.domain.item.dto.response.ItemResponse;
import com.ssafy.yam.domain.item.entity.Item;
import com.ssafy.yam.domain.item.repository.ItemRepository;

import com.ssafy.yam.domain.user.entity.User;
import com.ssafy.yam.domain.user.repository.UserRepository;
import com.ssafy.yam.utils.TokenUtils;
import lombok.RequiredArgsConstructor;
import org.modelmapper.ModelMapper;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ItemService {

    private final ItemRepository itemRepository;
    private final BookmarkRepository bookmarkRepository;
    private final ImageRepository imageRepository;
    private final UserRepository userRepository;
    private final DealService dealService;


    public ItemDetailResponse getItemByItemId(int itemId){
        Item item = itemRepository.findItemByItemId(itemId);
        User owner = item.getSeller();
        ItemResponse response = ItemResponse.builder()
                .itemId(itemId)
                .itemCategory(item.getItemCategory())
                .itemName(item.getItemName())
                .itemContent(item.getItemContent())
                .itemPrice(item.getItemPrice())
                .itemAddress(item.getItemAddress())
                .itemAreaCode(item.getItemAreaCode())
                .itemModifiedTime(item.getItemModifiedTime())
                .build();

        response.setOwner(
                ItemResponse.Owner.builder()
                        .ownerId(owner.getUserId())
                        .ownerNickName(owner.getUserNickname())
                        .ownerAddress(owner.getUserAddress())
                        .ownerImageUrl(owner.getUserImageUrl())
                        .build()
        );

        int bookmarkCnt = bookmarkRepository.countByItemId(itemId);
        if(bookmarkCnt == 0)
            response.setBookmark("N");
        else
            response.setBookmark("Y");
        response.setBookmarkCount(bookmarkCnt);

        List<Image> images = imageRepository.findAllByItem_ItemId(itemId);

        response.setItemImage(images.stream()
                .map(Image::getImageUrl)
                .collect(Collectors.toList()));

        List<LocalDate> deal = dealService.getUnavailableDate(itemId);

        ItemDetailResponse itemDetail = new ItemDetailResponse(response, deal);
        return itemDetail;
    }

    public List<ItemListResponse> getItemList(String token, Pageable pageable){
        List<ItemListResponse> response = new ArrayList<>();

        if(token.equals("")) {
            List<Item> itemList = itemRepository.findAllBy(pageable);
            addItemList(response, itemList);
        }else{
            String tokenEmail = TokenUtils.getUserEmailFromToken(token);
            User user = userRepository.findByUserEmail(tokenEmail).orElseThrow(() -> new IllegalArgumentException("?????? ????????? ????????????."));
            String areaCode = user.getUserAreaCode();
            List<Item> itemList = itemRepository.findAllByItemAreaCode(areaCode, pageable);
            addItemList(response, itemList);
        }
        return response;
    }

    private void addItemList(List<ItemListResponse> response, List<Item> itemList) {
        for (Item item : itemList) {
            ItemListResponse listItem = ItemListResponse.builder()
                    .itemId(item.getItemId())
                    .itemName(item.getItemName())
                    .itemPrice(item.getItemPrice())
                    .itemAddress(item.getItemAddress())
                    .itemAreaCode(item.getItemAreaCode())
                    .itemModifiedTime(item.getItemModifiedTime())
                    .build();

            Image image = imageRepository.findAllByItem_ItemIdLimit1(item.getItemId());
            if (image != null)
                listItem.setItemImage(image.getImageUrl());

            response.add(listItem);
        }
    }

}
