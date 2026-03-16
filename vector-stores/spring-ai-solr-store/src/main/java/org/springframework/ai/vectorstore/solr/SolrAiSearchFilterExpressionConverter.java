/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.vectorstore.solr;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

/**
 * SolrAiSearchFilterExpressionConverter is a class that converts Filter.Expression
 * objects into Solr query string representation. It extends the AbstractFilter
 * ExpressionConverter class.
 *
 * @author Jemin Huh
 * @since 1.0.0
 */
public class SolrAiSearchFilterExpressionConverter extends AbstractFilterExpressionConverter {

	private static final Pattern DATE_FORMAT_PATTERN = Pattern.compile("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}Z");

	private final SimpleDateFormat dateFormat;

	public SolrAiSearchFilterExpressionConverter() {
		this.dateFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		this.dateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	@Override
	protected void doExpression(Expression expression, StringBuilder context) {
		switch (expression.type()) {
			case AND, OR -> {
				this.convertOperand(expression.left(), context);
				context.append(getOperationSymbol(expression));
				this.convertOperand(expression.right(), context);
			}
			case EQ -> {
				this.convertOperand(expression.left(), context);
				context.append(":");
				this.convertOperand(expression.right(), context);
			}
			case NE -> {
				this.convertOperand(expression.left(), context);
				context.append(": NOT ");
				this.convertOperand(expression.right(), context);
			}
			case LT -> {
				this.convertOperand(expression.left(), context);
				context.append(":[* TO ");
				this.convertOperand(expression.right(), context);
				context.append("}");
			}
			case LTE -> {
				this.convertOperand(expression.left(), context);
				context.append(":[* TO ");
				this.convertOperand(expression.right(), context);
				context.append("]");
			}
			case GT -> {
				this.convertOperand(expression.left(), context);
				context.append(":{");
				this.convertOperand(expression.right(), context);
				context.append(" TO *]");
			}
			case GTE -> {
				this.convertOperand(expression.left(), context);
				context.append(":[");
				this.convertOperand(expression.right(), context);
				context.append(" TO *]");
			}
			case IN -> {
				this.convertOperand(expression.left(), context);
				context.append(":(");
				this.convertOperand(expression.right(), context);
				context.append(")");
			}
			case NIN -> {
				context.append("NOT ");
				this.convertOperand(expression.left(), context);
				context.append(":(");
				this.convertOperand(expression.right(), context);
				context.append(")");
			}
			default -> throw new RuntimeException("Not supported expression type: " + expression.type());
		}
	}

	@Override
	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
	}

	@Override
	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
	}

	@Override
	protected void doAddValueRangeSpitter(Filter.Value listValue, StringBuilder context) {
		context.append(" OR ");
	}

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> " AND ";
			case OR -> " OR ";
			default -> "";
		};
	}

	@Override
	public void doKey(Key key, StringBuilder context) {
		var identifier = hasOuterQuotes(key.key()) ? removeOuterQuotes(key.key()) : key.key();
		context.append("metadata.").append(identifier.trim());
	}

	@Override
	protected void doValue(Filter.Value filterValue, StringBuilder context) {
		if (filterValue.value() instanceof List list) {
			int c = 0;
			for (Object v : list) {
				context.append(v);
				if (c++ < list.size() - 1) {
					this.doAddValueRangeSpitter(filterValue, context);
				}
			}
		}
		else {
			this.doSingleValue(filterValue.value(), context);
		}
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		if (value instanceof Date date) {
			context.append(this.dateFormat.format(date));
		}
		else if (value instanceof String text) {
			if (DATE_FORMAT_PATTERN.matcher(text).matches()) {
				try {
					Date date = this.dateFormat.parse(text);
					context.append(this.dateFormat.format(date));
				}
				catch (ParseException e) {
					throw new IllegalArgumentException("Invalid date type:" + text, e);
				}
			}
			else {
				context.append(text);
			}
		}
		else {
			context.append(value);
		}
	}

	@Override
	public void doStartGroup(Filter.Group group, StringBuilder context) {
		context.append("(");
	}

	@Override
	public void doEndGroup(Filter.Group group, StringBuilder context) {
		context.append(")");
	}

}
