import PropTypes from "prop-types";
import styled from "styled-components";
import { PROBLEM_LABEL_FONT_SIZE } from "../../config";
import { isShouldUseDarkColor } from "../../utils/colors";

const StyledProblemLabel = styled.div`
  width: 28px;
  height: 100%;
  position: relative;
  font-size: ${PROBLEM_LABEL_FONT_SIZE};
  color: ${({darkText}) => darkText && "#000"};
  font-weight: bold;
  background: ${props => props.backgroundColor};
  
  display: flex;
  justify-content: center;
  align-items: center;
`;

export const ProblemLabel = ({letter, problemColor, className}) => {
    const dark = isShouldUseDarkColor(problemColor);
    console.log(dark);
    return <StyledProblemLabel
        backgroundColor={problemColor}
        darkText={dark}
        className={className}>
        {letter}
    </StyledProblemLabel>;
};

ProblemLabel.propTypes = {
    letter: PropTypes.string,
    problemColor: PropTypes.string,
    className: PropTypes.string
}
