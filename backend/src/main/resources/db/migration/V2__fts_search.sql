create virtual table event_search using fts5 (
    title,
    description,
    content = 'event',
    content_rowid = 'id',
    tokenize = 'unicode61'
);

create trigger event_search_ai after insert on event
begin
    insert into event_search (rowid, title, description)
    values (new.id, new.title, new.description);
end;

create trigger event_search_ad after delete on event
begin
    insert into event_search (event_search, rowid, title, description)
    values ('delete', old.id, old.title, old.description);
end;

create trigger event_search_au after update on event
begin
    insert into event_search (event_search, rowid, title, description)
    values ('delete', old.id, old.title, old.description);
    insert into event_search (rowid, title, description)
    values (new.id, new.title, new.description);
end;
