package org.deltafi.actionkit.coerce;

import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

public class StringCoercing implements Coercing<String, String> {

    @Override
    public String serialize(Object dataFetcherResult) throws CoercingSerializeException {
        return "\"\"" + dataFetcherResult.toString() + "\"\"";
    }

    @Override
    public String parseValue(Object input) throws CoercingParseValueException {
        return input.toString();
    }

    @Override
    public String parseLiteral(Object input) throws CoercingParseLiteralException {
        return input.toString();
    }
}
