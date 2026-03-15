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

package org.springframework.ai.vectorstore.idol;

import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.converter.AbstractFilterExpressionConverter;

/**
 * Converts {@link Filter.Expression} into IDOL FieldText filter expression format.
 *
 * @author pdavie
 */
public class IdolFilterExpressionConverter extends AbstractFilterExpressionConverter {

	@Override
	protected void doExpression(Filter.Expression expression, StringBuilder context) {
		switch (expression.type()) {
			case EQ -> {
				context.append("MATCH{");
				this.convertOperand(expression.right(), context);
				context.append("}:");
				this.convertOperand(expression.left(), context);
			}
			case NE -> {
				context.append("NOT MATCH{");
				this.convertOperand(expression.right(), context);
				context.append("}:");
				this.convertOperand(expression.left(), context);
			}
			case GT -> {
				context.append("GREATER{");
				this.convertOperand(expression.right(), context);
				context.append("}:");
				this.convertOperand(expression.left(), context);
			}
			case GTE -> {
				context.append("NRANGE{");
				this.convertOperand(expression.right(), context);
				context.append(",.}:");
				this.convertOperand(expression.left(), context);
			}
			case LT -> {
				context.append("LESS{");
				this.convertOperand(expression.right(), context);
				context.append("}:");
				this.convertOperand(expression.left(), context);
			}
			case LTE -> {
				context.append("NRANGE{.,");
				this.convertOperand(expression.right(), context);
				context.append("}:");
				this.convertOperand(expression.left(), context);
			}
			case IN -> {
				context.append("MATCH{");
				this.convertOperand(expression.right(), context);
				context.append("}:");
				this.convertOperand(expression.left(), context);
			}
			case NIN -> {
				context.append("NOT MATCH{");
				this.convertOperand(expression.right(), context);
				context.append("}:");
				this.convertOperand(expression.left(), context);
			}
			case AND -> {
				context.append("(");
				this.convertOperand(expression.left(), context);
				context.append(" AND ");
				this.convertOperand(expression.right(), context);
				context.append(")");
			}
			case OR -> {
				context.append("(");
				this.convertOperand(expression.left(), context);
				context.append(" OR ");
				this.convertOperand(expression.right(), context);
				context.append(")");
			}
			case NOT -> {
				context.append("NOT ");
				this.convertOperand(expression.left(), context);
			}
			default -> throw new IllegalArgumentException("Unsupported filter expression type: " + expression.type());
		}
	}

	@Override
	protected void doKey(Filter.Key filterKey, StringBuilder context) {
		context.append(filterKey.key());
	}

	@Override
	protected void doStartValueRange(Filter.Value listValue, StringBuilder context) {
		// Used for MATCH{val1,val2}:FIELD
	}

	@Override
	protected void doEndValueRange(Filter.Value listValue, StringBuilder context) {
		// Used for MATCH{val1,val2}:FIELD
	}

	@Override
	protected void doSingleValue(Object value, StringBuilder context) {
		context.append(value);
	}

	@Override
	protected void doAddValueRangeSpitter(Filter.Value listValue, StringBuilder context) {
		context.append(",");
	}

}
