-- 사전 연락: 이용권을 받을 수 있는 마감(closes_at)과 받은 이용권을 쓸 수 있는 기한(credit_expires_at)을 나눈다.
-- 초대 코드(invite_codes.closes_at, credit_expires_at)와 같은 방식. closes_at이 지난 뒤에는 사전 연락이 아니라 일반 가입으로 본다.
-- NULL이면 받을 수 있는 마감이 없다.
ALTER TABLE pre_contacts ADD COLUMN closes_at timestamptz;

-- 지금 있는 행은 지금까지처럼 이용권 기한을 받을 수 있는 마감으로 쓴다
UPDATE pre_contacts SET closes_at = credit_expires_at;
