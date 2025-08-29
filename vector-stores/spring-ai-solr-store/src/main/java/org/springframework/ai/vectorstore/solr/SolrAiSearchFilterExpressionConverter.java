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

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.Filter.Expression;
import org.springframework.ai.vectorstore.filter.Filter.Key;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.regex.Pattern;

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
			case AND:
			case OR:
				context.append("(");
				this.convertOperand(expression.left(), context);
				context.append(") ");
				context.append(getOperationSymbol(expression));
				context.append(" (");
				this.convertOperand(expression.right(), context);
				context.append(")");
				return;

			case NOT:
				context.append("NOT ");
				this.convertOperand(expression.left(), context);
				return;

			case NIN:
				context.append("NOT ");
			case IN:
				this.convertOperand(expression.left(), context);
				context.append("(");
				this.convertOperand(expression.right(), context);
				context.append(")");
				return;

			case NE:
				context.append("NOT ");
			case EQ:
				context.append(expression.right());
				return;

			case GT:
				this.handleRange(context, expression.left(), "{", expression.right(), true, "}");
				return;

			case LT:
				this.handleRange(context, expression.left(), "{", expression.left(), false, "}");
				return;

			case GTE:
				this.handleRange(context, expression.left(), "[", expression.left(), true, "]");
				return;

			case LTE:
				this.handleRange(context, expression.left(), "[", expression.left(), false, "]");
				return;
		}

		if (expression.type() == Filter.ExpressionType.IN || expression.type() == Filter.ExpressionType.NIN) {
			context.append(getOperationSymbol(expression));
			context.append("(");
			this.convertOperand(expression.left(), context);
			this.convertOperand(expression.right(), context);
			context.append(")");
		}
		else {
			this.convertOperand(expression.left(), context);
			context.append(getOperationSymbol(expression));
			this.convertOperand(expression.right(), context);
		}
	}

	private void handleRange(StringBuilder context, Filter.Operand leftOperand, String startRangeOp,
			Filter.Operand rightOperand, boolean leftWild, String endRangeOp) {
		this.convertOperand(leftOperand, context);
		context.append(startRangeOp);
		if (leftWild) {
			context.append("* TO ");
			this.convertOperand(rightOperand, context);
		}
		else {
			this.convertOperand(rightOperand, context);
			context.append(" TO *");
		}
		context.append(endRangeOp);
	}

	@Override
	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
	}

	@Override
	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
	}

	@Override
	protected void doAddValueRangeSpitter(Filter.Value listValue, StringBuilder context) {
	}

	private String getOperationSymbol(Expression exp) {
		return switch (exp.type()) {
			case AND -> " AND ";
			case OR -> " OR ";
			case EQ, IN -> "";
			case NE -> " NOT ";
			case LT -> "<";
			case LTE -> "<=";
			case GT -> ">";
			case GTE -> ">=";
			case NIN -> "NOT ";
			default -> throw new RuntimeException("Not supported expression type: " + exp.type());
		};
	}

	@Override
	public void doKey(Key key, StringBuilder context) {
		var identifier = hasOuterQuotes(key.key()) ? removeOuterQuotes(key.key()) : key.key();
		var prefixedIdentifier = withMetaPrefix(identifier);
		context.append(prefixedIdentifier.trim()).append(":");
	}

	public String withMetaPrefix(String identifier) {
		return identifier;
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
