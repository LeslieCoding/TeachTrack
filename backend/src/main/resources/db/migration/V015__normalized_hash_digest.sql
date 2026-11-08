-- Keep the normalized lookup key fixed-length so long stems cannot exceed btree index limits.
update questions
set normalized_hash = encode(sha256(convert_to(normalized_hash, 'UTF8')), 'hex');
