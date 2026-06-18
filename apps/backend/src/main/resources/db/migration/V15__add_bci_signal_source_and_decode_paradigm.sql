-- Closed-loop BCI upgrade: signal invasiveness tier on datasets + neural decoding paradigm on training jobs.
alter table datasets add column signal_source varchar(40) not null default 'non-invasive-eeg';
alter table training_jobs add column decode_paradigm varchar(60);
