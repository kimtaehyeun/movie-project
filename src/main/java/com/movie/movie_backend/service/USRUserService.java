package com.movie.movie_backend.service;

import com.movie.movie_backend.dto.UserDto;
import com.movie.movie_backend.dto.UserJoinRequestDto;
import com.movie.movie_backend.entity.User;
import com.movie.movie_backend.entity.Tag;
import com.movie.movie_backend.entity.MovieDetail;
import com.movie.movie_backend.entity.PasswordResetToken;
import com.movie.movie_backend.repository.USRUserRepository;
import com.movie.movie_backend.repository.PRDTagRepository;
import com.movie.movie_backend.repository.PRDMovieRepository;
import com.movie.movie_backend.repository.PasswordResetTokenRepository;
import com.movie.movie_backend.constant.UserRole;
import com.movie.movie_backend.service.PersonalizedRecommendationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional
public class USRUserService {
    private final USRUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final MailService mailService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PRDTagRepository tagRepository;
    private final PRDMovieRepository movieRepository;
    private final PersonalizedRecommendationService recommendationService;

    public User register(User user) {
        // 회원가입 로직
        return userRepository.save(user);
    }

    public User login(String username, String password) {
        // 로그인 로직 (실제 구현 시 비밀번호 암호화/검증 필요)
        Optional<User> user = userRepository.findByUsername(username);
        if (user.isPresent() && user.get().getPassword().equals(password)) {
            return user.orElse(null);
        }
        return null;
    }

    // 로그인 인증 메서드
    public User authenticate(String loginId, String password) {
        Optional<User> userOpt = userRepository.findByLoginId(loginId);
        if (userOpt.isPresent()) {
            User user = userOpt.get();
            if (passwordEncoder.matches(password, user.getPassword())) {
                return user;
            }
        }
        return null;
    }

    public User updateUserInfo(Long userId, User update) {
        // 회원정보 수정 로직
        User user = userRepository.findById(userId).orElse(null);
        if (user != null) {
            user.setEmail(update.getEmail());
            user.setPassword(update.getPassword());
            return userRepository.save(user);
        }
        return null;
    }

    public List<UserDto> getAllUsers() {
        List<User> users = userRepository.findAll();
        return users.stream()
                .map(UserDto::fromEntity)
                .collect(Collectors.toList());
    }

    public void join(UserJoinRequestDto requestDto) {
        // 이메일 인증 확인
        boolean isCodeValid = mailService.verifyAndRemoveCode(requestDto.getEmail(), requestDto.getVerificationCode());
        if (!isCodeValid) {
            throw new IllegalArgumentException("이메일 인증 코드가 올바르지 않습니다.");
        }

        // 비밀번호 확인 검증
        if (!requestDto.getPassword().equals(requestDto.getPasswordConfirm())) {
            throw new IllegalArgumentException("비밀번호가 일치하지 않습니다.");
        }
        
        // 아이디 중복 검사
        if (userRepository.existsByLoginId(requestDto.getLoginId())) {
            throw new IllegalArgumentException("이미 사용 중인 아이디입니다.");
        }
        
        // 이메일 중복 검사
        if (userRepository.existsByEmail(requestDto.getEmail())) {
            throw new IllegalArgumentException("이미 사용 중인 이메일입니다.");
        }
        
        // 비밀번호 암호화
        String encodedPassword = passwordEncoder.encode(requestDto.getPassword());
        
        // User 엔티티 생성 (이메일 인증 완료 상태로)
        User user = User.builder()
                .loginId(requestDto.getLoginId())
                .password(encodedPassword)
                .email(requestDto.getEmail())
                .nickname(requestDto.getNickname())
                .role(UserRole.USER)
                .emailVerified(true) // 최종 가입 시 인증 상태를 true로 설정
                .socialJoinCompleted(true) // 반드시 true로!
                .build();
        
        // 저장
        userRepository.save(user);
    }
    
    @Transactional(readOnly = true)
    public boolean checkLoginIdDuplicate(String loginId) {
        return userRepository.existsByLoginId(loginId);
    }
    
    @Transactional(readOnly = true)
    public boolean checkEmailDuplicate(String email) {
        return userRepository.existsByEmail(email);
    }

    // 닉네임 중복 확인
    @Transactional(readOnly = true)
    public boolean checkNicknameDuplicate(String nickname) {
        return userRepository.existsByNickname(nickname);
    }

    // 닉네임 추천 (영화 관련 + 유명 영화 캐릭터)
    public List<String> recommendNicknames() {
        String[] movieAdjectives = {
            "시네마", "액션", "로맨스", "스릴러", "판타지", "애니메이션", "SF", "느와르", "뮤지컬", "명작", "클래식", "마블", "DC", "흥행", "레전드", "감독님", "배우", "평론가", "주인공", "히어로", "악당", "명탐정", "마법사", "OST", "시사회", "엔딩크레딧", "필름", "팝콘"
        };
        String[] movieCharacters = {
            "해리포터", "반지의제왕", "프로도", "간달프", "아라곤", "토르", "아이언맨", "캡틴아메리카", "배트맨", "조커", "슈퍼맨", "스파이더맨", "블랙위도우", "헐크", "닥터스트레인지", "엘사", "안나", "올라프", "인디아나존스", "터미네이터", "로키", "다스베이더", "요다", "루크", "한솔로", "에이리언", "고질라", "킹콩", "007", "제임스본드", "에단헌트", "존윅", "네모", "도리", "알라딘", "자스민", "심바", "무파사", "나탈리", "아멜리에"
        };
        String[] movieNouns = {"덕후", "매니아", "광", "러버", "마스터", "헌터", "킹", "여신", "짱", "고수", "초보", "감상러", "수집가", "추천러", "리뷰어", "팬", "주인", "감독", "배우", "평론가"};
        List<String> nicknames = new ArrayList<>();
        Random random = new Random();
        int tryCount = 0;
        while (nicknames.size() < 3 && tryCount < 30) {
            String first = random.nextBoolean() ? movieAdjectives[random.nextInt(movieAdjectives.length)] : movieCharacters[random.nextInt(movieCharacters.length)];
            String noun = movieNouns[random.nextInt(movieNouns.length)];
            String number = random.nextInt(10) < 3 ? String.valueOf(100 + random.nextInt(900)) : "";
            String nickname = first + noun + number;
            if (!checkNicknameDuplicate(nickname) && !nicknames.contains(nickname)) {
                nicknames.add(nickname);
            }
            tryCount++;
        }
        return nicknames;
    }

    @Transactional
    public void updateNicknameByEmail(String email, String nickname) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setNickname(nickname);
        user.setSocialJoinCompleted(true);
        userRepository.save(user);
    }

    @Transactional
    public void updateNicknameByProviderAndProviderId(String provider, String providerId, String nickname) {
        System.out.println("[DEBUG] updateNicknameByProviderAndProviderId - provider: " + provider + ", providerId: " + providerId + ", nickname: " + nickname);
        User user = userRepository.findByProviderAndProviderId(provider, providerId)
                .orElse(null);
        if (user == null) {
            System.out.println("[DEBUG] User not found for provider: " + provider + ", providerId: " + providerId);
            throw new IllegalArgumentException("사용자를 찾을 수 없습니다.");
        }
        System.out.println("[DEBUG] User found: id=" + user.getId() + ", email=" + user.getEmail());
        user.setNickname(nickname);
        user.setSocialJoinCompleted(true);
        userRepository.save(user);
        System.out.println("[DEBUG] Nickname updated successfully.");
    }

    // 비밀번호 재설정 토큰 생성 및 저장
    public PasswordResetToken createPasswordResetToken(String email) {
        // 기존 토큰 삭제(1인 1토큰 정책)
        passwordResetTokenRepository.deleteByEmail(email);
        String token = java.util.UUID.randomUUID().toString();
        PasswordResetToken resetToken = PasswordResetToken.create(token, email, 30); // 30분 유효
        return passwordResetTokenRepository.save(resetToken);
    }

    // 토큰 검증
    public PasswordResetToken validatePasswordResetToken(String token) {
        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token)
                .orElseThrow(() -> new IllegalArgumentException("유효하지 않은 토큰입니다."));
        if (resetToken.isExpired() || resetToken.isUsed()) {
            throw new IllegalArgumentException("만료되었거나 이미 사용된 토큰입니다.");
        }
        return resetToken;
    }

    // 비밀번호 변경 및 토큰 무효화
    @Transactional
    public void resetPassword(String token, String newPassword) {
        PasswordResetToken resetToken = validatePasswordResetToken(token);
        User user = userRepository.findByEmail(resetToken.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        user.setPassword(passwordEncoder.encode(newPassword));
        resetToken.setUsed(true);
        passwordResetTokenRepository.save(resetToken);
        userRepository.save(user);
    }

    // 사용자 선호 장르 태그 조회
    @Transactional(readOnly = true)
    public List<String> getPreferredGenres(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return user.getPreferredTags().stream()
                .map(Tag::getName)
                .collect(Collectors.toList());
    }

    // 사용자 선호 장르 태그 저장/수정 (전체 교체)
    @Transactional
    public void setPreferredGenres(Long userId, List<String> genreTagNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<Tag> genreTags = tagRepository.findGenreTags();
        // 입력받은 태그명만 필터링
        List<Tag> selectedTags = new ArrayList<>();
        for (Tag tag : genreTags) {
            if (genreTagNames.contains(tag.getName())) {
                selectedTags.add(tag);
            }
        }
        user.setPreferredTags(selectedTags);
        userRepository.save(user);
        // 추천 캐시 무효화
        recommendationService.evictUserRecommendations(userId);
    }

    // 사용자 선호 태그 조회 (모든 카테고리)
    @Transactional(readOnly = true)
    public List<String> getPreferredTags(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        return user.getPreferredTags().stream()
                .map(Tag::getName)
                .collect(Collectors.toList());
    }

    // 사용자 선호 태그 저장/수정 (모든 카테고리)
    @Transactional
    public void setPreferredTags(Long userId, List<String> tagNames) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<Tag> genreTags = tagRepository.findGenreTags();
        // 입력받은 태그명 중 장르 태그만 필터링
        List<Tag> selectedTags = new ArrayList<>();
        for (Tag tag : genreTags) {
            if (tagNames.contains(tag.getName())) {
                selectedTags.add(tag);
            }
        }
        
        System.out.println("[선호태그 변경] userId=" + userId + ", 기존 태그=" + user.getPreferredTags().stream().map(Tag::getName).collect(Collectors.toList()) + ", 새 태그=" + selectedTags.stream().map(Tag::getName).collect(Collectors.toList()));
        
        user.setPreferredTags(selectedTags);
        userRepository.save(user);
        
        // 추천 캐시 무효화
        System.out.println("[선호태그 변경] 추천 캐시 무효화 호출");
        recommendationService.evictUserRecommendations(userId);
        // 추가로 모든 캐시 무효화 (확실성을 위해)
        recommendationService.evictAllRecommendations();
    }

    // 추천 캐시 완전 삭제 (디버깅용)
    public void clearRecommendationCache(Long userId) {
        System.out.println("[캐시 삭제] userId=" + userId + "의 추천 캐시를 완전 삭제합니다.");
        recommendationService.evictAllRecommendations();
    }

    // 사용자 특징 태그 제거 (장르 태그만 남김)
    @Transactional
    public void removeFeatureTags(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        List<Tag> genreTags = tagRepository.findGenreTags();
        List<Tag> currentTags = user.getPreferredTags();
        
        // 현재 태그 중 장르 태그만 필터링
        List<Tag> filteredTags = currentTags.stream()
                .filter(tag -> genreTags.stream().anyMatch(genreTag -> genreTag.getName().equals(tag.getName())))
                .collect(Collectors.toList());
        
        user.setPreferredTags(filteredTags);
        userRepository.save(user);
        // 추천 캐시 무효화
        recommendationService.evictUserRecommendations(userId);
    }

    // 사용자 선호 태그 기반 영화 추천
    @Transactional(readOnly = true)
    public List<String> getRecommendedMovies(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("사용자를 찾을 수 없습니다."));
        
        List<Tag> preferredTags = user.getPreferredTags();
        
        if (preferredTags.isEmpty()) {
            // 선호 태그가 없으면 빈 리스트 반환 (마이페이지에서 설정하라고 안내)
            return new ArrayList<>();
        }
        
        // 선호 태그를 가진 영화들을 모두 모아서 랜덤으로 20개 추출
        List<MovieDetail> allCandidates = movieRepository.findMoviesByTags(preferredTags);
        Collections.shuffle(allCandidates); // 무작위 섞기
        return allCandidates.stream()
                .limit(20)
                .map(MovieDetail::getMovieCd)
                .collect(Collectors.toList());
    }

    /**
     * 팔로우: followerId가 followingId를 팔로우
     */
    public void followUser(Long followerId, Long followingId) {
        if (followerId.equals(followingId)) throw new IllegalArgumentException("자기 자신은 팔로우할 수 없습니다.");
        User follower = userRepository.findById(followerId).orElseThrow(() -> new IllegalArgumentException("팔로우하는 유저가 존재하지 않습니다."));
        User following = userRepository.findById(followingId).orElseThrow(() -> new IllegalArgumentException("팔로우 대상 유저가 존재하지 않습니다."));
        if (!follower.getFollowing().contains(following)) {
            follower.getFollowing().add(following);
            userRepository.save(follower);
            userRepository.flush();
        }
    }

    /**
     * 언팔로우: followerId가 followingId를 언팔로우
     */
    public void unfollowUser(Long followerId, Long followingId) {
        User follower = userRepository.findById(followerId).orElseThrow(() -> new IllegalArgumentException("언팔로우하는 유저가 존재하지 않습니다."));
        User following = userRepository.findById(followingId).orElseThrow(() -> new IllegalArgumentException("언팔로우 대상 유저가 존재하지 않습니다."));
        if (follower.getFollowing().contains(following)) {
            follower.getFollowing().remove(following);
            userRepository.save(follower);
            userRepository.flush();
        }
    }

    /**
     * 내가 팔로우하는 유저 목록
     */
    @Transactional(readOnly = true)
    public Set<User> getFollowing(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));
        return user.getFollowing();
    }

    /**
     * 나를 팔로우하는 유저 목록
     */
    @Transactional(readOnly = true)
    public Set<User> getFollowers(Long userId) {
        User user = userRepository.findById(userId).orElseThrow(() -> new IllegalArgumentException("유저가 존재하지 않습니다."));
        return user.getFollowers();
    }
} 
