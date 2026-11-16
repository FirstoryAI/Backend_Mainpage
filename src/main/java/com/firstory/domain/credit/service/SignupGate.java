package com.firstory.domain.credit.service;

import com.firstory.domain.credit.entity.CreditSource;
import com.firstory.domain.credit.entity.InviteCode;
import com.firstory.domain.credit.entity.InviteTicket;
import com.firstory.domain.credit.entity.PreContact;
import com.firstory.domain.credit.entity.SignupGrant;
import com.firstory.domain.credit.entity.StoryCredit;
import com.firstory.domain.credit.repository.InviteCodeRepository;
import com.firstory.domain.credit.repository.PreContactRepository;
import com.firstory.domain.credit.repository.StoryCreditRepository;
import com.firstory.domain.user.entity.User;
import com.firstory.global.config.FirstoryProperties;
import com.firstory.global.error.BusinessException;
import com.firstory.global.error.ErrorCode;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

/**
 * 베타 가입 관문. 가입 경로(SignupSource)와 무료 이용권을 정한다. 이메일 가입과 카카오 가입이 함께 쓴다.
 *
 * <ol>
 *   <li>개발용 이메일(firstory.beta.unlimited-emails): 언제든 가입, 이용권 없이 무제한 (DEV)</li>
 *   <li>사전 연락 이메일(pre_contacts): 초대 링크 없이 가입, 그 행의 무료 이용권 (PRE_CONTACT)</li>
 *   <li>초대 링크로 들어온 세션: 가입할 때 자리를 잡고 초대 코드의 무료 이용권 (INVITE)</li>
 *   <li>그 밖: 베타 동안(invite-only)은 가입 불가, 아니면 무료 1회 (GENERAL)</li>
 * </ol>
 * 무료 이용권은 한 종류만 준다. 초대·사전 연락으로 받았으면 일반 1회는 없다.
 */
@Service
@RequiredArgsConstructor
public class SignupGate {

    static final String TICKET_ATTRIBUTE = InviteTicket.class.getName();
    static final int SIGNUP_FREE_CREDITS = 1;

    private final InviteCodeRepository inviteCodeRepository;
    private final PreContactRepository preContactRepository;
    private final StoryCreditRepository creditRepository;
    private final FirstoryProperties properties;
    private final Clock clock;

    /**
     * 초대 링크. 열려 있으면 세션에 표시를 남긴다. 자리는 가입할 때 잡는다.
     *
     * @return 표시를 남겼으면 true, 없는 코드이거나 마감됐으면 false
     */
    @Transactional(readOnly = true)
    public boolean acceptInvite(String code, HttpSession session) {
        Optional<InviteCode> invite = code == null ? Optional.empty() : inviteCodeRepository.findByCode(code.trim());
        if (invite.isEmpty() || !invite.get().isOpen(clock.instant())) {
            return false;
        }
        session.setAttribute(TICKET_ATTRIBUTE, new InviteTicket(invite.get().getId(), clock.instant()));
        return true;
    }

    /**
     * 가입을 시작해도 되는지 미리 본다 (가입 인증번호 받기). 자리를 잡지는 않는다.
     */
    @Transactional(readOnly = true)
    public void checkCanSignUp(String email, HttpSession session) {
        if (!properties.beta().inviteOnly()) {
            return;
        }
        if (properties.beta().isUnlimited(email) || availablePreContact(email).isPresent()) {
            return;
        }
        InviteTicket ticket = ticket(session);
        if (ticket == null) {
            throw new BusinessException(ErrorCode.SIGNUP_INVITE_REQUIRED);
        }
        boolean open = inviteCodeRepository.findById(ticket.inviteCodeId())
                .map(code -> code.isOpen(clock.instant())).orElse(false);
        if (!open) {
            throw new BusinessException(ErrorCode.INVITE_CLOSED);
        }
    }

    /**
     * 가입 트랜잭션 안에서 회원을 저장하기 전에 부른다. 초대 링크면 이때 자리를 잡는다.
     *
     * @param email 소문자로 맞춘 이메일
     */
    @Transactional
    public SignupGrant resolve(String email, HttpSession session) {
        if (properties.beta().isUnlimited(email)) {
            return SignupGrant.dev();
        }
        Optional<PreContact> preContact = preContactRepository.findByEmailForUpdate(email)
                .filter(contact -> !contact.isGranted());
        if (preContact.isPresent()) {
            return SignupGrant.preContact(preContact.get());
        }
        InviteTicket ticket = ticket(session);
        if (ticket != null) {
            Instant now = clock.instant();
            if (inviteCodeRepository.claim(ticket.inviteCodeId(), now) == 1) {
                return SignupGrant.invite(inviteCodeRepository.findById(ticket.inviteCodeId()).orElseThrow());
            }
            if (properties.beta().inviteOnly()) {
                throw new BusinessException(ErrorCode.INVITE_CLOSED);
            }
        }
        if (properties.beta().inviteOnly()) {
            throw new BusinessException(ErrorCode.SIGNUP_INVITE_REQUIRED);
        }
        return SignupGrant.general();
    }

    /**
     * 회원을 저장한 뒤 같은 트랜잭션에서 부른다. 가입 경로를 남기고 무료 이용권을 준다.
     */
    @Transactional
    public void apply(User user, SignupGrant grant, HttpSession session) {
        user.markSignupSource(grant.source());
        Instant now = clock.instant();
        switch (grant.source()) {
            case DEV -> {
                // 이용권 없이 무제한 (CreditService가 이메일로 본다)
            }
            case PRE_CONTACT -> {
                PreContact contact = grant.preContact();
                creditRepository.save(StoryCredit.grant(user, CreditSource.PRE_CONTACT, contact.getCreditAmount(),
                        contact.getCreditExpiresAt(), null));
                contact.markGranted(user, now);
            }
            case INVITE -> {
                InviteCode code = grant.inviteCode();
                creditRepository.save(StoryCredit.grant(user, CreditSource.INVITE, code.getCreditAmount(),
                        code.getCreditExpiresAt(), code));
            }
            case GENERAL -> creditRepository.save(
                    StoryCredit.grant(user, CreditSource.SIGNUP_FREE, SIGNUP_FREE_CREDITS, null, null));
        }
        session.removeAttribute(TICKET_ATTRIBUTE);
    }

    private Optional<PreContact> availablePreContact(String email) {
        return preContactRepository.findByEmail(User.normalizeEmail(email)).filter(contact -> !contact.isGranted());
    }

    private static InviteTicket ticket(HttpSession session) {
        return session == null ? null : (InviteTicket) session.getAttribute(TICKET_ATTRIBUTE);
    }
}
