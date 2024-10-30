-- replace the image column with separate image name and tag columns
ALTER TABLE plugins
    ADD COLUMN image_name text,
    ADD COLUMN image_tag text;

UPDATE plugins
SET
    image_name = CASE
        -- no colon then no tag use the image
        WHEN POSITION(':' IN image) = 0 THEN image
        -- no slash or the last colon is after the last slash then there is a tag
        WHEN POSITION('/' IN image) = 0 OR LENGTH(image) - POSITION(':' IN REVERSE(image)) > LENGTH(image) - POSITION('/' IN REVERSE(image))
            THEN substring(image, 0, LENGTH(image) - POSITION(':' IN REVERSE(image)) + 1)
        -- no tag found use the image
        ELSE
            image
        END,
    image_tag = CASE
        -- no image (i.e. system plugin)
        WHEN LENGTH(image) = 0
            THEN null
        -- no tag use latest
        WHEN POSITION(':' IN image) = 0 THEN 'latest'
        -- no slash or the last colon is after the slash then there is a tag
        WHEN POSITION('/' IN image) = 0 OR LENGTH(image) - POSITION(':' IN REVERSE(image)) > LENGTH(image) - POSITION('/' IN REVERSE(image))
            THEN substring(image, LENGTH(image) - POSITION(':' IN REVERSE(image)) + 2)
        -- no tag found, use latest
        ELSE
            'latest'
        END;

ALTER TABLE plugins
    DROP COLUMN image;