package com.eventoscelebrativos.service;

import com.eventoscelebrativos.dto.request.CommentatorRequestDTO;
import com.eventoscelebrativos.dto.response.CommentatorResponseDTO;

import java.util.List;

public interface CommentatorService {

    CommentatorResponseDTO createCommentator (CommentatorRequestDTO commentatorRequestDTO);
    List<CommentatorResponseDTO> findAllCommentators();
    CommentatorResponseDTO findCommentatorById(Long id);
    CommentatorResponseDTO updateCommentator(Long id, CommentatorRequestDTO commentatorRequestDTO);
    void deleteCommentatorById(Long id);

}
